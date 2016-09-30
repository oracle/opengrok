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
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.Executor;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(SubversionRepository.class);

    private static final long serialVersionUID = 1L;

    private static final String ENV_SVN_USERNAME = "OPENGROK_SUBVERSION_USERNAME";
    private static final String ENV_SVN_PASSWORD = "OPENGROK_SUBVERSION_PASSWORD";

    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY
            = "org.opensolaris.opengrok.history.Subversion";
    /**
     * The command to use to access the repository if none was given explicitly
     */
    public static final String CMD_FALLBACK = "svn";

    private static final String[] backupDatePatterns = new String[]{
        "yyyy-MM-dd'T'HH:mm:ss.'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",};

    protected String reposPath;

    public SubversionRepository() {
        type = "Subversion";
        datePattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    }

    private String getValue(Node node) {
        if (node == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
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

            List<String> cmd = new ArrayList<>();
            cmd.add(RepoCommand);
            cmd.add("info");
            cmd.add("--xml");
            File directory = new File(getDirectoryName());

            Executor executor = new Executor(cmd, directory);
            if (executor.exec() == 0) {
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document document = builder.parse(executor.getOutputStream());

                    String url
                            = getValue(document.getElementsByTagName("url").item(0));
                    if (url == null) {
                        LOGGER.log(Level.WARNING,
                                        "svn info did not contain an URL for [{0}]. Assuming remote repository.",
                                        directoryName);
                        setRemote(true);
                    } else {
                        if (!url.startsWith("file")) {
                            setRemote(true);
                        }
                    }
                    String root
                            = getValue(document.getElementsByTagName("root").item(0));
                    if (url != null && root != null) {
                        reposPath = url.substring(root.length());
                        rootFound = Boolean.TRUE;
                    }
                } catch (SAXException saxe) {
                    LOGGER.log(Level.WARNING,
                            "Parser error parsing svn output", saxe);
                } catch (ParserConfigurationException pce) {
                    LOGGER.log(Level.WARNING,
                            "Parser configuration error parsing svn output", pce);
                } catch (IOException ioe) {
                    LOGGER.log(Level.WARNING,
                            "IOException reading from svn process", ioe);
                }
            } else {
                LOGGER.log(Level.WARNING,
                                "Failed to execute svn info for [{0}]. Repository disabled.",
                                directoryName);
            }

            setWorking(rootFound);
        }
    }

    /**
     * Get an executor to be used for retrieving the history log for the named
     * file.
     *
     * @param file The file to retrieve history for
     * @param sinceRevision the revision number immediately preceding the first
     *                      revision we want, or {@code null} to fetch the entire
     *                      history
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(final File file, String sinceRevision) {
        String abs;
        try {
            abs = file.getCanonicalPath();
        } catch (IOException exp) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get canonical path: {0}", exp.getClass().toString());
            return null;
        }
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("log");
        cmd.add("--non-interactive");
        cmd.addAll(getAuthCommandLineParams());
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
        if (filename.length() > 0) {
            cmd.add(escapeFileName(filename));
        }

        return new Executor(cmd, new File(directoryName), sinceRevision != null);
    }

    @Override
    public DateFormat getDateFormat() {
        return new DateFormat() {
            private DateFormat formatter = new SimpleDateFormat(datePattern, Locale.getDefault());
            private DateFormat[] backupFormatters = new DateFormat[backupDatePatterns.length];

            {
                for (int i = 0; i < backupDatePatterns.length; i++) {
                    backupFormatters[i] = new SimpleDateFormat(backupDatePatterns[i], Locale.getDefault());
                }
            }

            @Override
            public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
                return formatter.format(date, toAppendTo, fieldPosition);
            }

            @Override
            public Date parse(String source) throws ParseException {
                try {
                    return formatter.parse(source);
                } catch (ParseException ex) {
                    for (int i = 0; i < backupFormatters.length; i++) {
                        try {
                            return backupFormatters[i].parse(source);
                        } catch (ParseException ex1) {
                        }
                    }
                    throw ex;
                }
            }

            @Override
            public Date parse(String source, ParsePosition pos) {
                return formatter.parse(source, pos);
            }
        };
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        File directory = new File(directoryName);

        String filepath;
        try {
            filepath = (new File(parent, basename)).getCanonicalPath();
        } catch (IOException exp) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get canonical path: {0}", exp.getClass().toString());
            return null;
        }
        String filename = filepath.substring(directoryName.length() + 1);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
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
                Attributes attr) {
            sb.setLength(0);
            if (null != qname) {
                switch (qname) {
                    case "entry":
                        rev = null;
                        author = null;
                        break;
                    case "commit":
                        rev = attr.getValue("revision");
                        break;
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qname) {
            if (null != qname) {
                switch (qname) {
                    case "author":
                        author = sb.toString();
                        break;
                    case "entry":
                        annotation.addLine(rev, author, true);
                        break;
                }
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
        } catch (ParserConfigurationException | SAXException ex) {
            IOException err = new IOException("Failed to create SAX parser", ex);
            throw err;
        }

        ArrayList<String> argv = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("annotate");
        argv.addAll(getAuthCommandLineParams());
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
        Annotation ret = null;
        try {
            process = pb.start();
            AnnotateHandler handler = new AnnotateHandler(file.getName());
            try (BufferedInputStream in
                    = new BufferedInputStream(process.getInputStream())) {
                saxParser.parse(in, handler);
                ret = handler.annotation;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE,
                        "An error occurred while parsing the xml output", e);
            }
        } finally {
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

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("update");
        cmd.addAll(getAuthCommandLineParams());
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
            working = checkCmd(RepoCommand, "--help");
        }
        return working;
    }

    private List<String> getAuthCommandLineParams() {
        List<String> result = new ArrayList<>();
        String userName = System.getenv(ENV_SVN_USERNAME);
        String password = System.getenv(ENV_SVN_PASSWORD);
        if (userName != null && !userName.isEmpty() && password != null
                && !password.isEmpty()) {
            result.add("--username");
            result.add(userName);
            result.add("--password");
            result.add(password);
        }
        result.add("--trust-server-cert");
        return result;
    }

    @Override
    String determineParent() throws IOException {
        String parent = null;
        File directory = new File(directoryName);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("info");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(directory);
        Process process;
        process = pb.start();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("URL:")) {
                    String parts[] = line.split("\\s+");
                    if (parts.length != 2) {
                        LOGGER.log(Level.WARNING,
                                "Failed to get parent for {0}", directoryName);
                    }
                    parent = parts[1];
                    break;
                }
            }
        }

        return parent;
    }

    @Override
    String determineBranch() {
        return null;
    }
}
