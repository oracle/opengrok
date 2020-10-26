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
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.BufferSink;
import org.opengrok.indexer.util.Executor;

/**
 * Access to a Monotone repository.
 *
 * @author Trond Norbye
 */
public class MonotoneRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonotoneRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY = "org.opengrok.indexer.history.Monotone";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "mnt";

    public MonotoneRepository() {
        type = "Monotone";
        datePatterns = new String[]{
            "yyyy-MM-dd'T'hh:mm:ss"
        };
    }

    @Override
    boolean getHistoryGet(
            BufferSink sink, String parent, String basename, String rev) {

        File directory = new File(getDirectoryName());
        try {
            String filename = (new File(parent, basename)).getCanonicalPath()
                    .substring(getDirectoryName().length() + 1);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String[] argv = {RepoCommand, "cat", "-r", rev, filename};
            Executor executor = new Executor(Arrays.asList(argv), directory,
                    RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
            copyBytes(sink, executor.getOutputStream());
            return true;
        } catch (Exception exp) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get history: {0}", exp.getClass().toString());
        }

        return false;
    }

    /**
     * Get an executor to be used for retrieving the history log for the named
     * file or directory.
     *
     * @param file The file or directory to retrieve history for
     * @param sinceRevision the oldest changeset to return from the executor, or
     *                  {@code null} if all changesets should be returned
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(File file, String sinceRevision)
            throws IOException {
        String filename = getRepoRelativePath(file);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("log");

        if (sinceRevision != null) {
            cmd.add("--to");
            cmd.add(sinceRevision);
        }

        cmd.add("--no-graph");
        cmd.add("--no-merges");
        cmd.add("--no-format-dates");
        cmd.add(filename);

        return new Executor(cmd, new File(getDirectoryName()), sinceRevision != null);
    }

    /**
     * Annotate the specified file/revision using the {@code mnt annotate} command.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     * @throws java.io.IOException if I/O exception occurred or the command failed
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        ArrayList<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("annotate");
        cmd.add(getQuietOption());
        if (revision != null) {
            cmd.add("-r");
            cmd.add(revision);
        }
        cmd.add(file.getName());
        File directory = new File(getDirectoryName());

        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        MonotoneAnnotationParser parser = new MonotoneAnnotationParser(file);
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
        return true;
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        File f = new File(file, "_MTN");
        return f.exists() && f.isDirectory();
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
    History getHistory(File file) throws HistoryException {
        return getHistory(file, null);
    }

    @Override
    History getHistory(File file, String sinceRevision)
            throws HistoryException {
        return new MonotoneHistoryParser(this).parse(file, sinceRevision);
    }

    private String getQuietOption() {
        if (useDeprecated()) {
            return "--reallyquiet";
        } else {
            return "--quiet --quiet";
        }
    }

    public static final String DEPRECATED_KEY
            = "org.opengrok.indexer.history.monotone.deprecated";

    private boolean useDeprecated() {
        return Boolean.parseBoolean(System.getProperty(DEPRECATED_KEY, "false"));
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        String parent = null;
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("ls");
        cmd.add("vars");
        cmd.add("database");
        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        executor.exec();

        try (BufferedReader in = new BufferedReader(executor.getOutputReader())) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("database") && line.contains("default-server")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length != 3) {
                        LOGGER.log(Level.WARNING,
                                "Failed to get parent for {0}", getDirectoryName());
                    }
                    parent = parts[2];
                    break;
                }
            }
        }

        return parent;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) {
        return null;
    }

    @Override
    String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        return null;
    }
}
