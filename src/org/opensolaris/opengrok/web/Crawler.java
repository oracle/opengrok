/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.web;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.opensolaris.opengrok.util.Getopt;

/**
 * This is a small (and extremely simple) web-crawler that can be used to
 * generate load on an OpenGrok server.
 * 
 * @author Trond Norbye
 */
public class Crawler implements Runnable {

    private static Fifo urls;

    /**
     * Program entry point
     * 
     * @param argv argument vector
     */
    public static void main(String[] argv) {
        int noThreads = 10;
        URL root = null;
        Getopt getopt = new Getopt(argv, "r:t:d");
        try {
            getopt.parse();
        } catch (ParseException ex) {
            System.err.println("OpenGrok: " + ex.getMessage());
            System.err.println("Usage: [-d] [-t nothreads] [-r url]");
            System.exit(1);
        }

        try {
            root = new URL("http://localhost/source/xref");
        } catch (Exception e) {
        }

        int cmd;
        while ((cmd = getopt.getOpt()) != -1) {
            switch (cmd) {
                case 'r':
                    try {
                        root = new URL(getopt.getOptarg());
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                    break;
                case 't':
                    noThreads = Integer.parseInt(getopt.getOptarg());
                    break;
                case 'd':
                    Fifo.dump();
                    System.exit(1);
                    break;
            }
        }
        urls = new Fifo();
        urls.add(root);

        List<Thread> threads = new ArrayList<Thread>();
        for (int ii = 0; ii < noThreads; ++ii) {
            Thread thread = new Thread(new Crawler());
            thread.start();
            threads.add(thread);
        }

        // wait for all threads to terminate!
        for (Thread thread : threads) {
            boolean term = false;
            do {
                try {
                    thread.join();
                    term = true;
                } catch (Exception e) {
                }
            } while (!term);
        }

        System.exit(0);
    }

    public boolean downloadPage(URL page) {
        boolean ret = true;
        BufferedReader in = null;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(page.getProtocol());
            sb.append("://");
            sb.append(page.getHost());
            if (page.getPort() != 80) {
                sb.append(":" + page.getPort());
            }

            int len = sb.length();

            in = new BufferedReader(new InputStreamReader(page.openStream()));
            if (!page.openConnection().getContentType().startsWith("text/html")) {
                return false;
            }

            String content;

            while ((content = in.readLine()) != null) {
                int idx = 0;
                do {
                    idx = content.indexOf("<a href=\"", idx);
                    if (idx != -1) {
                        sb.setLength(len);
                        int stop = content.indexOf("\"", idx + 9);
                        String link = content.substring(idx + 9, stop);
                        if (!(link.startsWith("http") ||
                                link.startsWith("..") ||
                                link.startsWith("ftp") ||
                                link.startsWith("/s?"))) {
                            if (link.startsWith("/")) {
                                sb.append(link);
                            } else {
                                sb.append(page.getPath());
                                sb.append("/");
                                sb.append(link);
                            }

                            try {
                                urls.add(new URL(sb.toString()));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }
                        idx = stop + 1;
                    } else {
                        break;
                    }

                } while (true);
            }

        } catch (Exception e) {
            if (!(e instanceof FileNotFoundException)) {
                e.printStackTrace();
            }

            ret = false;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        if (!ret) {
            return false;
        }

        return ret;
    }

    public void run() {
        while (true) {
            URL url = urls.next();
            try {
                if (url != null) {
                    if (!downloadPage(url)) {
                        urls.failed(url);
                    }
                } else {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
            }
        }
    }

    /**
     * A small utility class that acts like a FIFO, but it silently ignores
     * frequent (by default 1) entries. It will also drop links links if the 
     * FIFO is too long (default 1000 entries).
     */
    private static class Fifo {

        private Queue<URL> urls;
        private Connection db;
        private int limit = 1;
        private int url_queue_len = 1000;

        public Fifo() {
            urls = new ConcurrentLinkedQueue<URL>();
            createDb();
        }

        public static void dump() {
            try {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            } catch (ClassNotFoundException exp) {
                exp.printStackTrace();
                return;
            }

            String strUrl = "jdbc:derby:OpenGrokCrawler";
            Connection db = null;
            try {
                db = DriverManager.getConnection(strUrl);
            } catch (SQLException ex) {
                ex.printStackTrace();
                return;
            }

            try {
                PreparedStatement st = db.prepareStatement("SELECT url,requests,failures FROM Crawler");
                ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    System.out.print(rs.getString(1));
                    System.out.print("  ");
                    System.out.print(rs.getInt(2));
                    System.out.print("  ");
                    System.out.println(rs.getInt(3));
                }
                db.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        private void createDb() {
            try {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            } catch (ClassNotFoundException exp) {
                return;
            }

            String connectStr = "jdbc:derby:OpenGrokCrawler";

            try {
                db = DriverManager.getConnection(connectStr);
            } catch (SQLException ex) {
            }

            if (db == null) {
                connectStr = connectStr + ";create=true";
                try {
                    db = DriverManager.getConnection(connectStr);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }

                try {
                    PreparedStatement st = db.prepareStatement("CREATE table Crawler ( url VARCHAR(255) primary key, requests int, failures int)");
                    st.execute();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        }

        public void add(URL url) {
            boolean add = true;
            if (db != null) {
                synchronized (db) {
                    PreparedStatement statement = null;
                    try {
                        statement = db.prepareStatement("SELECT url,requests FROM Crawler WHERE url=?");
                        statement.setString(1, url.toString());
                        ResultSet rs = statement.executeQuery();
                        if (rs.next()) {
                            int tot = rs.getInt(2);
                            if (tot < limit) {
                                statement = db.prepareStatement("UPDATE Crawler set requests=? WHERE url=?");
                                statement.setInt(1, tot + 1);
                                statement.setString(2, url.toString());
                                statement.execute();
                            } else {
                                add = false;
                            }
                        } else {
                            statement = db.prepareStatement("INSERT INTO Crawler(url, requests, failures) VALUES (?,?,?)");
                            statement.setString(1, url.toString());
                            statement.setInt(2, 1);
                            statement.setInt(3, 0);
                            statement.execute();
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        System.exit(1);
                    }
                }
            }

            if (add && urls.size() < url_queue_len) {
                urls.add(url);
            }
        }

        public void failed(URL url) {
            if (db != null) {
                synchronized (db) {
                    PreparedStatement statement = null;
                    try {
                        statement = db.prepareStatement("UPDATE Crawler set failures=failures + 1 WHERE url=?");
                        statement.setString(1, url.toString());
                        statement.execute();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        System.exit(1);
                    }
                }
            }
        }

        public URL next() {
            return urls.poll();
        }
    }
}
