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
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.Executor;

/**
 * Access to a CVS repository.
 */
public class CVSRepository extends RCSRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CVSRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for repository.
     */
    public static final String CMD_PROPERTY_KEY
            = "org.opensolaris.opengrok.history.cvs";
    /**
     * The command to use to access the repository if none was given explicitly
     */
    public static final String CMD_FALLBACK = "cvs";

    private Boolean isBranch = null;
    private String branch = null;

    /**
     * Pattern used to extract author/revision from cvs annotate.
     */
    private static final Pattern ANNOTATE_PATTERN
            = Pattern.compile("([\\.\\d]+)\\W+\\((\\w+)");

    public CVSRepository() {
        /**
         * This variable is set in the anchestor to TRUE which has a side effect
         * that this repository is always marked as working even though it does
         * not have the binary available on the system.
         *
         * Setting this to null does restores the default behavior (as java
         * default for reference is null) for this repository - detecting the
         * binary and act upon that.
         *
         * @see #isWorking
         */
        working = null;
        setType("CVS");
        datePatterns = new String[]{
            "yyyy-MM-dd hh:mm:ss"
        };
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(RepoCommand, "--version");
        }
        return working;
    }

    @Override
    public void setDirectoryName(String directoryName) {
        super.setDirectoryName(directoryName);

        if (isWorking()) {
            File rootFile = new File(getDirectoryName() + File.separatorChar
                    + "CVS" + File.separatorChar + "Root");
            BufferedReader input;
            String root;
            try {
                input = new BufferedReader(new FileReader(rootFile));
                try {
                    root = input.readLine();
                } catch (java.io.IOException e) {
                    LOGGER.log(Level.WARNING, "failed to load: {0}", e);
                    return;
                } finally {
                    try {
                        input.close();
                    } catch (java.io.IOException e) {
                        LOGGER.log(Level.INFO, "failed to close: {0}", e);
                    }
                }
            } catch (java.io.FileNotFoundException e) {
                LOGGER.log(Level.FINE, "not loading CVS Root file: {0}", e);
                return;
            }

            if (!root.startsWith("/")) {
                setRemote(true);
            }
        }
    }

    @Override
    File getRCSFile(File file) {
        File cvsFile
                = RCSHistoryParser.getCVSFile(file.getParent(), file.getName());
        if (cvsFile != null && cvsFile.exists()) {
            return cvsFile;
        }
        return null;
    }

    @Override
    public boolean isRepositoryFor(File file) {
        if (file.isDirectory()) {
            File cvsDir = new File(file, "CVS");
            return cvsDir.isDirectory();
        }
        return false;
    }

    @Override
    public void update() throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("update");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }

    /**
     * Get an executor to be used for retrieving the history log for the named
     * file.
     *
     * @param file The file to retrieve history for
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(final File file) throws IOException {
        String abs = file.getCanonicalPath();
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("log");

        if (isBranch == null) {
            File tagFile = new File(getDirectoryName(), "CVS/Tag");
            if (tagFile.isFile()) {
                isBranch = Boolean.TRUE;
                try (BufferedReader br = new BufferedReader(new FileReader(tagFile))) {
                    String line = br.readLine();
                    if (line != null) {
                        branch = line.substring(1);
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING,
                            "Failed to work with CVS/Tag file of {0}",
                            getDirectoryName() + ": " + ex.getClass().toString());
                } catch (Exception exp) {
                    LOGGER.log(Level.WARNING,
                            "Failed to get revision tag of {0}",
                            getDirectoryName() + ": " + exp.getClass().toString());
                }
            } else {
                isBranch = Boolean.FALSE;
            }
        }

        if (isBranch.equals(Boolean.TRUE) && branch != null && !branch.isEmpty()) {
            // Just generate THIS branch history, we don't care about the other
            // branches which are not checked out.
            cmd.add("-r" + branch);
        } else {
            // Get revisions on this branch only (otherwise the revisions
            // list produced by the cvs log command would be unsorted).
            cmd.add("-b");
        }

        if (filename.length() > 0) {
            cmd.add(filename);
        }
        return new Executor(cmd, new File(getDirectoryName()));
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        Process process = null;
        String revision = rev;

        if (rev.indexOf(':') != -1) {
            revision = rev.substring(0, rev.indexOf(':'));
        }
        try {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String argv[] = {RepoCommand, "up", "-p", "-r", revision, basename};
            process = Runtime.getRuntime().exec(argv, null, new File(parent));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            try (InputStream in = process.getInputStream()) {
                int len;

                while ((len = in.read(buffer)) != -1) {
                    if (len > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }

            ret = new ByteArrayInputStream(out.toByteArray());
        } catch (Exception exp) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get history: {0}", exp.getClass().toString());
        } finally {
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

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    @Override
    public boolean fileHasHistory(File file) {
        // @TODO: Research how to cheaply test if a file in a given
        // CVS repo has history.  If there is a cheap test, then this
        // code can be refined, boosting performance.
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new CVSHistoryParser().parse(file, this);
    }

    @Override
    Annotation annotate(File file, String revision) throws IOException {
        ArrayList<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("annotate");
        if (revision != null) {
            cmd.add("-r");
            cmd.add(revision);
        }
        cmd.add(file.getName());

        Executor exec = new Executor(cmd, file.getParentFile());
        int status = exec.exec();

        if (status != 0) {
            LOGGER.log(Level.WARNING,
                    "Failed to get annotations for: \"{0}\" Exit code: {1}",
                    new Object[]{file.getAbsolutePath(), String.valueOf(status)});
        }

        return parseAnnotation(exec.getOutputReader(), file.getName());
    }

    protected Annotation parseAnnotation(Reader input, String fileName)
            throws IOException {
        BufferedReader in = new BufferedReader(input);
        Annotation ret = new Annotation(fileName);
        String line = "";
        int lineno = 0;
        boolean hasStarted = false;
        Matcher matcher = ANNOTATE_PATTERN.matcher(line);
        while ((line = in.readLine()) != null) {
            // Skip header
            if (!hasStarted && (line.length() == 0
                    || !Character.isDigit(line.charAt(0)))) {
                continue;
            }
            hasStarted = true;

            // Start parsing
            ++lineno;
            matcher.reset(line);
            if (matcher.find()) {
                String rev = matcher.group(1);
                String author = matcher.group(2).trim();
                ret.addLine(rev, author, true);
            } else {
                LOGGER.log(Level.SEVERE,
                        "Error: did not find annotation in line {0}: [{1}]",
                        new Object[]{String.valueOf(lineno), line});
            }
        }
        return ret;
    }

    @Override
    String determineParent() throws IOException {
        File rootFile = new File(directoryName + File.separator + "CVS"
                + File.separator + "Root");
        String parent = null;

        if (rootFile.isFile()) {
            try (BufferedReader br = new BufferedReader(new FileReader(rootFile))) {
                parent = br.readLine();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING,
                        "Failed to read CVS/Root file {0}: {1}",
                        new Object[]{rootFile, ex.getClass().toString()});
            }
        }

        return parent;
    }
}
