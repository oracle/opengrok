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
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.BufferSink;
import org.opengrok.indexer.util.Executor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

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
            = "org.opengrok.indexer.history.Subversion";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "svn";

    private static final String URLattr = "url";

    protected String reposPath;

    public SubversionRepository() {
        type = "Subversion";
        datePatterns = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };

        ignoredDirs.add(".svn");
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

    /**
     * Get {@code Document} corresponding to the parsed XML output from 
     * {@code svn info} command.
     * @return document with data from {@code info} or null if the {@code svn}
     * command failed
     */
    private Document getInfoDocument() {
        Document document = null;
        List<String> cmd = new ArrayList<>();

        cmd.add(RepoCommand);
        cmd.add("info");
        cmd.add("--xml");
        File directory = new File(getDirectoryName());

        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        if (executor.exec() == 0) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                document = builder.parse(executor.getOutputStream());
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
                            getDirectoryName());
        }

        return document;
    }

    /**
     * Get value of given tag in 'svn info' document.
     * @param document document object containing {@code info} contents
     * @param tagName name of the tag to return value for
     * @return value string
     */
    private String getInfoPart(Document document, String tagName) {
        return getValue(document.getElementsByTagName(tagName).item(0));
    }

    @Override
    public void setDirectoryName(File directory) {
        super.setDirectoryName(directory);

        if (isWorking()) {
            // set to true if we manage to find the root directory
            Boolean rootFound = Boolean.FALSE;

            Document document = getInfoDocument();
            if (document != null) {
                String url = getInfoPart(document, URLattr);
                if (url == null) {
                    LOGGER.log(Level.WARNING,
                            "svn info did not contain an URL for [{0}]. Assuming remote repository.",
                            getDirectoryName());
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
     * @param numEntries number of entries to return. If 0, return all.
     * @param cmdType command timeout type
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(final File file, String sinceRevision,
            int numEntries, CommandTimeoutType cmdType) throws IOException {

        String filename = getRepoRelativePath(file);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("log");
        cmd.add("--non-interactive");
        cmd.addAll(getAuthCommandLineParams());
        cmd.add("--xml");
        cmd.add("-v");
        if (numEntries > 0) {
            cmd.add("-l" + numEntries);
        }
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

        return new Executor(cmd, new File(getDirectoryName()),
                    RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
    }

    @Override
    boolean getHistoryGet(
            BufferSink sink, String parent, String basename, String rev) {

        File directory = new File(getDirectoryName());

        String filepath;
        try {
            filepath = (new File(parent, basename)).getCanonicalPath();
        } catch (IOException exp) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get canonical path: {0}", exp.getClass().toString());
            return false;
        }
        String filename = filepath.substring(getDirectoryName().length() + 1);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("cat");
        cmd.add("--non-interactive");
        cmd.addAll(getAuthCommandLineParams());
        cmd.add("-r");
        cmd.add(rev);
        cmd.add(escapeFileName(filename));

        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        if (executor.exec() == 0) {
            try {
                copyBytes(sink, executor.getOutputStream());
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to get content for {0}",
                        basename);
            }
        }

        return false;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return getHistory(file, null, 0, CommandTimeoutType.INDEXER);
    }

    @Override
    History getHistory(File file, String sinceRevision) throws HistoryException {
        return getHistory(file, sinceRevision, 0, CommandTimeoutType.INDEXER);
    }

    private History getHistory(File file, String sinceRevision, int numEntries,
            CommandTimeoutType cmdType)
            throws HistoryException {
        return new SubversionHistoryParser().parse(file, this, sinceRevision,
                numEntries, cmdType);
    }

    private String escapeFileName(String name) {
        if (name.length() == 0) {
            return name;
        }
        return name + "@";
    }

    @Override
    public Annotation annotate(File file, String revision) throws IOException {
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

        Executor executor = new Executor(argv, file.getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        SubversionAnnotationParser parser = new SubversionAnnotationParser(file.getName());
        int status = executor.exec(true, parser);
        if (status != 0) {
            LOGGER.log(Level.WARNING,
                    "Failed to get annotations for: \"{0}\" Exit code: {1}",
                    new Object[]{file.getAbsolutePath(), String.valueOf(status)});
            throw new IOException(executor.getErrorString());
        } else {
            return parser.getAnnotation();
        }
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
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
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

        return result;
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) {
        String part = null;
        Document document = getInfoDocument();

        if (document != null) {
            part = getInfoPart(document, URLattr);
        }

        return part;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) throws IOException {
        String branch = null;
        Document document = getInfoDocument();

        if (document != null) {
            String url = getInfoPart(document, URLattr);
            int idx;
            final String branchesStr = "branches/";
            if (url != null && (idx = url.indexOf(branchesStr)) > 0) {
                branch = url.substring(idx + branchesStr.length());
            }
        }

        return branch;
    }

    @Override
    public String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        String curVersion = null;

        try {
            History hist = getHistory(new File(getDirectoryName()), null, 1, cmdType);
            if (hist != null) {
                List<HistoryEntry> hlist = hist.getHistoryEntries();
                if (hlist != null && hlist.size() > 0) {
                    HistoryEntry he = hlist.get(0);
                    curVersion = format(he.getDate()) + " " +
                            he.getRevision() + " " + he.getAuthor() + " " +
                            he.getMessage();
                }
            }
        } catch (HistoryException ex) {
            LOGGER.log(Level.WARNING, "cannot get current version info for {0}",
                    getDirectoryName());
        }

        return curVersion;
    }
}
