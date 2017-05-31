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
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.Executor;

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
    public static final String CMD_PROPERTY_KEY
            = "org.opensolaris.opengrok.history.Monotone";
    /**
     * The command to use to access the repository if none was given explicitly
     */
    public static final String CMD_FALLBACK = "mnt";

    public MonotoneRepository() {
        type = "Monotone";
        datePatterns = new String[]{
            "yyyy-MM-dd'T'hh:mm:ss"
        };
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        File directory = new File(directoryName);

        Process process = null;
        String revision = rev;

        try {
            String filename = (new File(parent, basename)).getCanonicalPath()
                    .substring(directoryName.length() + 1);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String argv[] = {RepoCommand, "cat", "-r", revision, filename};
            process = Runtime.getRuntime().exec(argv, null, directory);

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

            ret = new BufferedInputStream(new ByteArrayInputStream(out.toByteArray()));
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
        String abs = file.getCanonicalPath();
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

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

        return new Executor(cmd, new File(directoryName), sinceRevision != null);
    }
    /**
     * Pattern used to extract author/revision from hg annotate.
     */
    private static final Pattern ANNOTATION_PATTERN
            = Pattern.compile("^(\\w+)\\p{Punct}\\p{Punct} by (\\S+)");

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     * @throws java.io.IOException
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
        File directory = new File(directoryName);

        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        Annotation ret;
        try (BufferedReader in = new BufferedReader(executor.getOutputReader())) {
            ret = new Annotation(file.getName());
            String line;
            String author = null;
            String rev = null;
            while ((line = in.readLine()) != null) {
                Matcher matcher = ANNOTATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    rev = matcher.group(1);
                    author = matcher.group(2);
                    ret.addLine(rev, author, true);
                } else {
                    ret.addLine(rev, author, true);
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
    public void update() throws IOException {
        File directory = new File(directoryName);
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);

        List<String> cmd = new ArrayList<>();
        cmd.add(RepoCommand);
        cmd.add("pull");
        cmd.add(getQuietOption());
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        cmd.clear();
        cmd.add(RepoCommand);
        cmd.add("update");
        cmd.add(getQuietOption());
        executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }

    @Override
    public boolean fileHasHistory(File file) {
        return true;
    }

    @Override
    boolean isRepositoryFor(File file) {
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
            = "org.opensolaris.opengrok.history.monotone.deprecated";

    private boolean useDeprecated() {
        return Boolean.parseBoolean(System.getProperty(DEPRECATED_KEY, "false"));
    }

    @Override
    String determineParent() throws IOException {
        String parent = null;
        File directory = new File(directoryName);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("ls");
        cmd.add("vars");
        cmd.add("database");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(directory);
        Process process;
        process = pb.start();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("database") && line.contains("default-server")) {
                    String parts[] = line.split("\\s+");
                    if (parts.length != 3) {
                        LOGGER.log(Level.WARNING,
                                "Failed to get parent for {0}", directoryName);
                    }
                    parent = parts[2];
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
