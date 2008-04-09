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
import java.net.URLConnection;
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

    private Fifo urls;

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
            e.printStackTrace();
            System.exit(1);
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

        Fifo.initialize();

        List<Thread> threads = new ArrayList<Thread>();
        for (int ii = 0; ii < noThreads; ++ii) {
            Thread thread = new Thread(new Crawler(root));
            root = null;
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

    public Crawler(URL root) {
        urls = new Fifo();
        if (root != null) {
            urls.add(root);
        }
    }

    public boolean downloadPage(URL page) {
        boolean ret = true;
        BufferedReader in = null;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(page.getProtocol());
            sb.append("://");
            sb.append(page.getHost());
            int port = page.getPort();
            if (port != -1 && port != 80) {
                sb.append(":" + page.getPort());
            }

            int len = sb.length();
            URLConnection connection = page.openConnection();
            if (connection == null) {
                return false;
            }

            String contentType = connection.getContentType();
            if (contentType == null || !contentType.startsWith("text/html")) {
                return false;
            }

            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String content;

            while ((content = in.readLine()) != null) {
                int idx = 0;
                do {
                    idx = content.indexOf("<a ", idx);
                    if (idx != -1) {
                        // is this a href?
                        idx += 3;
                        int end = content.indexOf('>', idx);
                        int href = content.indexOf("href=\"", idx);

                        if (end != -1 && href != -1 && href < end) {
                            idx = href + 6;
                            sb.setLength(len);
                            int stop = content.indexOf("\"", idx);
                            String link = content.substring(idx, stop);
                            if (!(link.startsWith("http") ||
                                    link.startsWith("..") ||
                                    link.startsWith("ftp") ||
                                    link.indexOf("/s?") != -1)) {
                                if (link.startsWith("/")) {
                                    sb.append(link);
                                } else {
                                    String path = page.getPath();
                                    sb.append(path);
                                    if (!path.endsWith("/")) {
                                        sb.append("/");
                                    }
                                    sb.append(link);
                                }

                                try {
                                    urls.add(new URL(sb.toString()));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            }
                            idx = stop;
                        }
                        ++idx;
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
        int ii = 180;
        while (ii > 0) {
            URL url = urls.next();
            try {
                if (url != null) {
                    if (!downloadPage(url)) {
                        urls.failed(url);
                    }
                    ii = 180;
                } else {
                    Thread.sleep(1000);
                    --ii;
                }
            } catch (Exception e) {
            }
        }
        System.out.println("Download thread is terminating (no new work the last 2 minutes)");
    }

    /**
     * A small utility class that acts like a FIFO, but it silently ignores
     * frequent (by default 1) entries. It will also drop links links if the 
     * FIFO is too long (default 1000 entries).
     */
    private static class Fifo {

        private static Queue<URL> urls;
        private int url_queue_len = 1000;
        private Connection db;
        private PreparedStatement updateFailed;
        private PreparedStatement findNext;
        private PreparedStatement updateNext;
        private PreparedStatement addUrl;

        public Fifo() {
            if (urls == null) {
                String strUrl = "jdbc:derby:OpenGrokCrawler";
                try {
                    db = DriverManager.getConnection(strUrl);
                    updateFailed = db.prepareStatement("UPDATE Crawler set failures=failures + 1 WHERE url=?");
                    findNext = db.prepareStatement("SELECT url FROM Crawler WHERE id IN (SELECT MIN(id) FROM Crawler WHERE requests=0)");
                    updateNext = db.prepareStatement("UPDATE Crawler set requests=requests + 1 WHERE url=?");
                    addUrl = db.prepareStatement("INSERT INTO Crawler(url, requests, failures) VALUES (?,0,0)");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
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

        private static void initialize() {
            try {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            } catch (ClassNotFoundException exp) {
                urls = new ConcurrentLinkedQueue<URL>();
                return;
            }

            String connectStr = "jdbc:derby:OpenGrokCrawler";
            Connection db = null;
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
                    PreparedStatement st = db.prepareStatement("CREATE table Crawler ( id integer unique not null generated always as identity (start with 1, increment by 1), url VARCHAR(255) primary key, requests int, failures int)");
                    st.execute();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        }

        public void add(URL url) {
            if (db != null) {
                try {
                    addUrl.setString(1, url.toString());
                    addUrl.execute();
                } catch (SQLException ex) {
                    if (ex.getSQLState().startsWith("23")) {
                        /* Item already exists.. */
                    } else {
                        ex.printStackTrace();
                        System.exit(1);
                    }
                }
            } else {
                if (urls.size() < url_queue_len) {
                    urls.add(url);
                }
            }
        }

        public void failed(URL url) {
            if (db != null) {
                try {
                    updateFailed.setString(1, url.toString());
                    updateFailed.execute();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            }
        }

        public URL next() {
            URL ret = null;

            if (db != null) {
                try {
                    ResultSet rs = findNext.executeQuery();
                    if (rs.next()) {
                        try {
                            ret = new URL(rs.getString(1));
                            rs.close();
                            updateNext.setString(1, ret.toString());
                            updateNext.execute();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    System.exit(1);
                }
            } else {
                ret = urls.poll();
            }
            return ret;
        }
    }
}
