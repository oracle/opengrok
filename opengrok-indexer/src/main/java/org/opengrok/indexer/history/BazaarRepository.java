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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * Access to a Bazaar repository.
 */
public class BazaarRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(BazaarRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY
            = "org.opengrok.indexer.history.Bazaar";
    /**
     * The command to use to access the repository if none was given explicitly
     */
    public static final String CMD_FALLBACK = "bzr";

    public BazaarRepository() {
        type = "Bazaar";
        datePatterns = new String[]{
            "EEE yyyy-MM-dd hh:mm:ss ZZZZ"
        };

        ignoredDirs.add(".bzr");
    }

    /**
     * Get an executor to be used for retrieving the history log for the named
     * file.
     *
     * @param file The file to retrieve history for
     * @param sinceRevision the oldest changeset to return from the executor, or
     *                      {@code null} if all changesets should be returned
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(final File file, final String sinceRevision)
            throws IOException {
        String filename = getRepoRelativePath(file);

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("log");

        if (file.isDirectory()) {
            cmd.add("-v");
        }
        cmd.add(filename);

        if (sinceRevision != null) {
            cmd.add("-r");
            cmd.add(sinceRevision + "..-1");
        }

        return new Executor(cmd, new File(getDirectoryName()), sinceRevision != null);
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        File directory = new File(getDirectoryName());

        Process process = null;
        try {
            String filename = (new File(parent, basename)).getCanonicalPath()
                    .substring(getDirectoryName().length() + 1);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String argv[] = {RepoCommand, "cat", "-r", rev, filename};
            process = Runtime.getRuntime().exec(argv, null, directory);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            InputStream in = process.getInputStream();
            int len;

            while ((len = in.read(buffer)) != -1) {
                if (len > 0) {
                    out.write(buffer, 0, len);
                }
            }

            ret = new ByteArrayInputStream(out.toByteArray());
        } catch (Exception exp) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get history: " + exp.getClass().toString(), exp);
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

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     * @throws java.io.IOException if I/O exception occurred
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("blame");
        cmd.add("--all");
        cmd.add("--long");
        if (revision != null) {
            cmd.add("-r");
            cmd.add(revision);
        }
        cmd.add(file.getName());

        Executor executor = new Executor(cmd, file.getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        BazaarAnnotationParser parser = new BazaarAnnotationParser(file.getName());
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
    public void update() throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("info");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        if (executor.getOutputString().contains("parent branch:")) {
            cmd.clear();
            cmd.add(RepoCommand);
            cmd.add("up");
            executor = new Executor(cmd, directory);
            if (executor.exec() != 0) {
                throw new IOException(executor.getErrorString());
            }
        }
    }

    @Override
    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether Bazaar has history
        // available for a file?
        // Otherwise, this is harmless, since Bazaar's commands will just
        // print nothing if there is no history.
        return true;
    }

    @Override
    boolean isRepositoryFor(File file, boolean interactive) {
        if (file.isDirectory()) {
            File f = new File(file, ".bzr");
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

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file, String sinceRevision) throws HistoryException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        History result = new BazaarHistoryParser(this).parse(file, sinceRevision);
        // Assign tags to changesets they represent
        // We don't need to check if this repository supports tags, because we know it:-)
        if (env.isTagsEnabled()) {
            assignTagsInHistory(result);
        }
        return result;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return getHistory(file, null);
    }

    @Override
    boolean hasFileBasedTags() {
        return true;
    }

    /**
     * @param directory Directory where we list tags
     */
    @Override
    protected void buildTagList(File directory, boolean interactive) {
        this.tagList = new TreeSet<>();
        ArrayList<String> argv = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("tags");

        Executor executor = new Executor(argv, directory, interactive ?
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout() :
                RuntimeEnvironment.getInstance().getCommandTimeout());
        final BazaarTagParser parser = new BazaarTagParser();
        int status = executor.exec(true, parser);
        if (status != 0) {
            LOGGER.log(Level.WARNING,
                    "Failed to get tags for: \"{0}\" Exit code: {1}",
                    new Object[]{directory.getAbsolutePath(), String.valueOf(status)});
        } else {
            tagList = parser.getEntries();
        }
    }

    @Override
    String determineParent(boolean interactive) throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("config");
        cmd.add("parent_location");
        Executor executor = new Executor(cmd, directory, interactive ?
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout() :
                RuntimeEnvironment.getInstance().getCommandTimeout());
        if (executor.exec(false) != 0) {
            throw new IOException(executor.getErrorString());
        }

        return executor.getOutputString().trim();
    }

    @Override
    String determineBranch(boolean interactive) {
        return null;
    }

    @Override
    String determineCurrentVersion(boolean interactive) throws IOException {
        return null;
    }
}
