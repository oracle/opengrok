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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Access to a Subversion repository.
 *
 * @todo The current implementation does <b>not</b> support nestet
 * repositories as described in http://svnbook.red-bean.com/en/1.0/ch07s03.html
 *
 * @author Trond Norbye
 */
public class SubversionRepository extends Repository {

    protected String reposPath;
    private static ScmChecker svnBinary = new ScmChecker(new String[]{
                getCommand(), "--help"
            });

    private static final String getCommand() {
        return System.getProperty("org.opensolaris.opengrok.history.Subversion", "svn");
    }

    private String getValue(Node node) {
        StringBuffer sb = new StringBuffer();
        Node n = node.getFirstChild();
        while (n != null) {
            if (n.getNodeType() == n.TEXT_NODE) {
                sb.append(n.getNodeValue());
            }

            n = n.getNextSibling();
        }
        return sb.toString();
    }

    @Override
    public void setDirectoryName(String directoryName) {
        super.setDirectoryName(directoryName);

        if (isWorking()) {
            String argv[] = new String[]{getCommand(), "info", "--xml"};
            File directory = new File(getDirectoryName());

            Process process = null;
            InputStream in = null;
            try {
                process = Runtime.getRuntime().exec(argv, null, directory);
                in = process.getInputStream();

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(in);

                String url = getValue(document.getElementsByTagName("url").item(0));
                String root = getValue(document.getElementsByTagName("root").item(0));

                reposPath = url.substring(root.length());
            } catch (SAXException saxe) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Parser error parsing svn output", saxe);                
            } catch (ParserConfigurationException pce) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Parser configuration error parsing svn output", pce);
            } catch (IOException ioe) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "IOException reading from svn process", ioe);
            } finally {

                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
                if (process != null) {
                    try {
                        process.exitValue();
                    } catch (IllegalThreadStateException exp) {
                        // the process is still running??? just kill it..
                        process.destroy();
                    }
                }
            }
        }
    }

    /**
     * Get a handle to a svn log process for the given file.
     *
     * @param file THe file to get subversion log from
     * @return A handle to the process, or null
     * @throws java.io.IOException if an error occurs
     */
    protected Process getHistoryLogProcess(final File file) throws IOException {
        String abs = file.getAbsolutePath();
        String filename;
        String directoryName = getDirectoryName();
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        } else {
            filename = "";
        }
        String argv[] = new String[]{getCommand(), "log", "--xml", "-v", filename};
        File directory = new File(getDirectoryName());
        return Runtime.getRuntime().exec(argv, null, directory);
    }

    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        String directoryName = getDirectoryName();
        File directory = new File(directoryName);

        String filename = (new File(parent, basename)).getAbsolutePath().substring(directoryName.length() + 1);
        Process process = null;
        InputStream in = null;
        try {
            String argv[] = {getCommand(), "cat", "-r", rev, filename};
            process = Runtime.getRuntime().exec(argv, null, directory);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            in = process.getInputStream();
            int len;

            while ((len = in.read(buffer)) != -1) {
                if (len > 0) {
                    out.write(buffer, 0, len);
                }
            }

            ret = new BufferedInputStream(new ByteArrayInputStream(out.toByteArray()));
        } catch (Exception exp) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to get history: " + exp.getClass().toString());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            // Clean up zombie-processes...
            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException exp) {
                    // the process is still running??? just kill it..
                    process.destroy();
                }
            }
        }

        return ret;
    }

    public Class<? extends HistoryParser> getHistoryParser() {
        return SubversionHistoryParser.class;
    }

    public Class<? extends HistoryParser> getDirectoryHistoryParser() {
        return SubversionHistoryParser.class;
    }

    private static class AnnotateHandler extends DefaultHandler2 {

        String rev;
        String author;
        final Annotation annotation;
        final StringBuilder sb;

        AnnotateHandler(String filename) {
            annotation = new Annotation(filename);
            sb = new StringBuilder();
        }

        @Override
        public void startElement(String uri, String localName, String qname, Attributes attr) throws SAXException {
            sb.setLength(0);
            if ("entry".equals(qname)) {
                rev = null;
                author = null;
            } else if ("commit".equals(qname)) {
                rev = attr.getValue("revision");
            }
        }

        @Override
        public void endElement(String uri, String localName, String qname) throws SAXException {
            if ("author".equals(qname)) {
                author = sb.toString();
            } else if ("entry".equals(qname)) {
                annotation.addLine(rev, author, true);
            }
        }

        @Override
        public void characters(char[] arg0, int arg1, int arg2) throws SAXException {
            sb.append(arg0, arg1, arg2);
        }
    }

    public Annotation annotate(File file, String revision) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = null;
        try {
            saxParser = factory.newSAXParser();
        } catch (ParserConfigurationException ex) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, null, ex);
        }
        if (saxParser == null) {
            return null;
        }


        ArrayList<String> argv = new ArrayList<String>();
        argv.add(getCommand());
        argv.add("annotate");
        argv.add("--xml");
        if (revision != null) {
            argv.add("-r");
            argv.add(revision);
        }
        argv.add(file.getName());
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(file.getParentFile());
        Process process = null;
        BufferedInputStream in = null;
        Annotation ret = null;
        try {
            process = pb.start();
            in = new BufferedInputStream(process.getInputStream());

            AnnotateHandler handler = new AnnotateHandler(file.getName());
            try {
                saxParser.parse(in, handler);
                ret = handler.annotation;
            } catch (Exception e) {
                OpenGrokLogger.getLogger().log(Level.SEVERE, "An error occurred while parsing the xml output", e);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Just ignore
                }
            }
            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException e) {
                    // the process is still running??? just kill it..
                    process.destroy();
                }
            }
        }
        return ret;
    }

    public boolean fileHasAnnotation(File file) {
        return true;
    }

    public boolean isCacheable() {
        return true;
    }

    public boolean fileHasHistory(File file) {
        // TODO: Research how to cheaply test if a file in a given
        // SVN repo has history.  If there is a cheap test, then this
        // code can be refined, boosting performance.
        return true;
    }

    public void update() throws Exception {
        Process process = null;

        try {
            File directory = new File(getDirectoryName());
            process = Runtime.getRuntime().exec(new String[]{getCommand(), "update"}, null, directory);
            boolean interrupted;
            do {
                interrupted = false;
                try {
                    if (process.waitFor() != 0) {
                        return;
                    }
                } catch (InterruptedException exp) {
                    interrupted = true;
                }
            } while (interrupted);
        } finally {

            // is this really the way to do it? seems a bit brutal...
            try {
                process.exitValue();
            } catch (IllegalThreadStateException e) {
                process.destroy();
            }
        }
    }

    @Override
    boolean isRepositoryFor( File file) {
        File f = new File(file, ".svn");
        return f.exists() && f.isDirectory();
    }

    @Override
    protected boolean isWorking() {
        return svnBinary.available;
    }
}
