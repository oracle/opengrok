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
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Access to a Subversion repository.
 *
 * <b>TODO</b> The current implementation does <b>not</b> support nested
 * repositories as described in http://svnbook.red-bean.com/en/1.0/ch07s03.html
 *
 * @author Trond Norbye
 */
public class SubversionRepository extends Repository {
    private static final long serialVersionUID = 1L;
    /** The property name used to obtain the client command for this repository. */
    public static final String CMD_PROPERTY_KEY =
        "org.opensolaris.opengrok.history.Subversion";
    /** The command to use to access the repository if none was given explicitly */
    public static final String CMD_FALLBACK = "svn";

    protected String reposPath;

    public SubversionRepository() {
        type = "Subversion";
        datePattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    }

    private String getValue(Node node) {
        if (node == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        Node n = node.getFirstChild();
        while (n != null) {
            if (n.getNodeType() == Node.TEXT_NODE) {
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
            // set to true if we manage to find the root directory
            Boolean rootFound = Boolean.FALSE;

            List<String> cmd = new ArrayList<String>();
            cmd.add(this.cmd);
            cmd.add("info");
            cmd.add("--xml");
            File directory = new File(getDirectoryName());

            Executor executor = new Executor(cmd, directory);
            if (executor.exec() == 0) {
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document document = builder.parse(executor.getOutputStream());

                    String url =
                        getValue(document.getElementsByTagName("url").item(0));
                    if (url == null) {
                        OpenGrokLogger.getLogger()
                            .warning("svn info did not contain an URL for ["
                                + directoryName
                                + "]. Assuming remote repository.");
                        setRemote(true);
                    } else {
                        if (!url.startsWith("file")) {
                            setRemote(true);
                        }
                    }
                    String root =
                        getValue(document.getElementsByTagName("root").item(0));
                    if (url != null && root != null) {
                        reposPath = url.substring(root.length());
                        rootFound = Boolean.TRUE;
                    }
                } catch (SAXException saxe) {
                    OpenGrokLogger.getLogger().log(Level.WARNING,
                        "Parser error parsing svn output", saxe);
                } catch (ParserConfigurationException pce) {
                    OpenGrokLogger.getLogger().log(Level.WARNING,
                        "Parser configuration error parsing svn output", pce);
                } catch (IOException ioe) {
                    OpenGrokLogger.getLogger().log(Level.WARNING,
                        "IOException reading from svn process", ioe);
                }
            } else {
                OpenGrokLogger.getLogger()
                        .warning("Failed to execute svn info for ["
                            + directoryName + "]. Repository disabled.");
            }

            setWorking(rootFound);
        }
    }

    /**
     * Get an executor to be used for retrieving the history log for the
     * named file.
     *
     * @param file The file to retrieve history for
     * @param sinceRevision the revision number immediately preceding the first
     * revision we want, or {@code null} to fetch the entire history
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(final File file, String sinceRevision) {
        String abs = file.getAbsolutePath();
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("log");
        cmd.add("--trust-server-cert");
        cmd.add("--non-interactive");
        cmd.add("--xml");
        cmd.add("-v");
        if (sinceRevision != null) {
            cmd.add("-r");
            // We would like to use sinceRevision+1 here, but if no new
            // revisions have been added after sinceRevision, it would fail
            // because there is no such revision as sinceRevision+1. Instead,
            // fetch the unneeded revision and remove it later.
            cmd.add("BASE:" + sinceRevision);
        }
        cmd.add(escapeFileName(filename));

        return new Executor(cmd, new File(directoryName));
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev)
    {
        InputStream ret = null;

        File directory = new File(directoryName);

        String filename = (new File(parent, basename)).getAbsolutePath()
            .substring(directoryName.length() + 1);

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("cat");
        cmd.add("-r");
        cmd.add(rev);
        cmd.add(escapeFileName(filename));

        Executor executor = new Executor(cmd, directory);
        if (executor.exec() == 0) {
            ret = executor.getOutputStream();
        }

        return ret;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return getHistory(file, null);
    }

    @Override
    History getHistory(File file, String sinceRevision)
            throws HistoryException {
        return new SubversionHistoryParser().parse(file, this, sinceRevision);
    }

    private String escapeFileName(String name) {
        if (name.length() == 0) {
            return name;
        }
        return name + "@";
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
        public void startElement(String uri, String localName, String qname,
            Attributes attr)
        {
            sb.setLength(0);
            if ("entry".equals(qname)) {
                rev = null;
                author = null;
            } else if ("commit".equals(qname)) {
                rev = attr.getValue("revision");
            }
        }

        @Override
        public void endElement(String uri, String localName, String qname) {
            if ("author".equals(qname)) {
                author = sb.toString();
            } else if ("entry".equals(qname)) {
                annotation.addLine(rev, author, true);
            }
        }

        @Override
        public void characters(char[] arg0, int arg1, int arg2) {
            sb.append(arg0, arg1, arg2);
        }
    }

    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = null;
        try {
            saxParser = factory.newSAXParser();
        } catch (Exception ex) {
            IOException err = new IOException("Failed to create SAX parser", ex);
            throw err;
        }

        ArrayList<String> argv = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(cmd);
        argv.add("annotate");
        argv.add("--trust-server-cert");
        argv.add("--non-interactive");
        argv.add("--xml");
        if (revision != null) {
            argv.add("-r");
            argv.add(revision);
        }
        argv.add(escapeFileName(file.getName()));
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
                OpenGrokLogger.getLogger().log(Level.SEVERE,
                    "An error occurred while parsing the xml output", e);
            }
        } finally {
            IOUtils.close(in);
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

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    @Override
    public boolean fileHasHistory(File file) {
        // @TODO: Research how to cheaply test if a file in a given
        // SVN repo has history.  If there is a cheap test, then this
        // code can be refined, boosting performance.
        return true;
    }

    @Override
    public void update() throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("update");
        cmd.add("--trust-server-cert");
        cmd.add("--non-interactive");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }

    @Override
    boolean isRepositoryFor(File file) {
       if (file.isDirectory()) {
        File f = new File(file, ".svn");
        return f.exists() && f.isDirectory();
       }
       return false;
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(new String[]{ cmd, "--help" });
        }
        return working.booleanValue();
    }
}
