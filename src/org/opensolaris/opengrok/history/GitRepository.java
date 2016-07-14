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
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.Executor;

/**
 * Access to a Git repository.
 *
 */
public class GitRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY
            = "org.opensolaris.opengrok.history.git";
    /**
     * The command to use to access the repository if none was given explicitly
     */
    public static final String CMD_FALLBACK = "git";

    /**
     * git blame command
     */
    private static final String BLAME = "blame";

    /**
     * arguments to shorten git IDs
     */
    private static final int CSET_LEN = 8;
    private static final String ABBREV_LOG = "--abbrev=" + CSET_LEN;
    private static final String ABBREV_BLAME = "--abbrev=" + (CSET_LEN - 1);

    /**
     * Pattern used to extract author/revision from git blame.
     */
    private static final Pattern BLAME_PATTERN
            = Pattern.compile("^\\W*(\\w+).+?\\((\\D+).*$");

    public GitRepository() {
        type = "git";
        datePattern = "EEE MMM dd hh:mm:ss yyyy ZZZZ";
    }

    /**
     * Get path of the requested file given a commit hash. Useful for tracking
     * the path when file has changed its location.
     *
     * @param fileName name of the file to retrieve the path
     * @param revision commit hash to track the path of the file
     * @return full path of the file on success; null string on failure
     */
    private String getCorrectPath(String fileName, String revision) throws IOException {
        List<String> cmd = new ArrayList<>();
        String path = "";

        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add(BLAME);
        cmd.add("-c"); // to get correctly formed changeset IDs
        cmd.add(ABBREV_LOG);
        cmd.add("-C");
        cmd.add(fileName);
        File directory = new File(directoryName);
        Executor exec = new Executor(cmd, directory);

        int status = exec.exec();
        if (status != 0) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get blame list in resolving correct path");
            return path;
        }
        try (BufferedReader in = new BufferedReader(exec.getOutputReader())) {
            String pattern = "^\\W*" + revision + " (.+?) .*$";
            Pattern commitPattern = Pattern.compile(pattern);
            String line = "";
            Matcher matcher = commitPattern.matcher(line);
            while ((line = in.readLine()) != null) {
                matcher.reset(line);
                if (matcher.find()) {
                    path = matcher.group(1);
                    break;
                }
            }
        }

        return path;
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
    Executor getHistoryLogExecutor(final File file, String sinceRevision)
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
        cmd.add("--follow");
        cmd.add("--abbrev-commit");
        cmd.add(ABBREV_LOG);
        cmd.add("--name-only");
        cmd.add("--pretty=fuller");

        if (sinceRevision != null) {
            cmd.add(sinceRevision + "..");
        }

        if (filename.length() > 0) {
            cmd.add(filename);
        }

        return new Executor(cmd, new File(getDirectoryName()), sinceRevision != null);
    }

    /**
     * Create a {@code Reader} that reads an {@code InputStream} using the
     * correct character encoding.
     *
     * @param input a stream with the output from a log or blame command
     * @return a reader that reads the input
     * @throws IOException if the reader cannot be created
     */
    Reader newLogReader(InputStream input) throws IOException {
        // Bug #17731: Git always encodes the log output using UTF-8 (unless
        // overridden by i18n.logoutputencoding, but let's assume that hasn't
        // been done for now). Create a reader that uses UTF-8 instead of the
        // platform's default encoding.
        return new InputStreamReader(input, "UTF-8");
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        File directory = new File(directoryName);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        Process process = null;
        try {
            String filename = (new File(parent, basename)).getCanonicalPath()
                    .substring(directoryName.length() + 1);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String argv[] = {RepoCommand, "show", rev + ":" + filename};
            process = Runtime.getRuntime().exec(argv, null, directory);

            InputStream in = process.getInputStream();
            int len;
            boolean error = true;

            while ((len = in.read(buffer)) != -1) {
                error = false;
                if (len > 0) {
                    output.write(buffer, 0, len);
                }
            }
            if (error) {
                process.destroy();
                String path = getCorrectPath(filename, rev);
                argv[2] = rev + ":" + path;
                process = Runtime.getRuntime().exec(argv, null, directory);
                in = process.getInputStream();
                while ((len = in.read(buffer)) != -1) {
                    if (len > 0) {
                        output.write(buffer, 0, len);
                    }
                }
            }

            ret = new ByteArrayInputStream(output.toByteArray());
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
     * @throws java.io.IOException
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add(BLAME);
        cmd.add("-c"); // to get correctly formed changeset IDs
        cmd.add(ABBREV_BLAME);
        if (revision != null) {
            cmd.add(revision);
        }
        cmd.add(file.getName());

        Executor exec = new Executor(cmd, file.getParentFile());
        int status = exec.exec();

        // File might have changed its location
        if (status != 0) {
            cmd.clear();
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            cmd.add(RepoCommand);
            cmd.add(BLAME);
            cmd.add("-c"); // to get correctly formed changeset IDs
            cmd.add(ABBREV_BLAME);
            cmd.add("-C");
            cmd.add(file.getName());
            exec = new Executor(cmd, file.getParentFile());
            status = exec.exec();
            if (status != 0) {
                LOGGER.log(Level.SEVERE,
                        "Failed to get blame list");
            }
            try (BufferedReader in = new BufferedReader(exec.getOutputReader())) {
                String pattern = "^\\W*" + revision + " (.+?) .*$";
                Pattern commitPattern = Pattern.compile(pattern);
                String line = "";
                Matcher matcher = commitPattern.matcher(line);
                while ((line = in.readLine()) != null) {
                    matcher.reset(line);
                    if (matcher.find()) {
                        String filepath = matcher.group(1);
                        cmd.clear();
                        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
                        cmd.add(RepoCommand);
                        cmd.add(BLAME);
                        cmd.add("-c"); // to get correctly formed changeset IDs
                        cmd.add(ABBREV_BLAME);
                        if (revision != null) {
                            cmd.add(revision);
                        }
                        cmd.add("--");
                        cmd.add(filepath);
                        File directory = new File(directoryName);
                        exec = new Executor(cmd, directory);
                        status = exec.exec();
                        if (status != 0) {
                            LOGGER.log(Level.SEVERE,
                                    "Failed to get blame details for modified file path");
                        }
                        break;
                    }
                }
            }
        }

        if (status != 0) {
            LOGGER.log(Level.WARNING,
                    "Failed to get annotations for: \"{0}\" Exit code: {1}",
                    new Object[]{file.getAbsolutePath(), String.valueOf(status)});
        }

        return parseAnnotation(
                newLogReader(exec.getOutputStream()), file.getName());
    }

    protected Annotation parseAnnotation(Reader input, String fileName)
            throws IOException {

        BufferedReader in = new BufferedReader(input);
        Annotation ret = new Annotation(fileName);
        String line = "";
        int lineno = 0;
        Matcher matcher = BLAME_PATTERN.matcher(line);
        while ((line = in.readLine()) != null) {
            ++lineno;
            matcher.reset(line);
            if (matcher.find()) {
                String rev = matcher.group(1);
                String author = matcher.group(2).trim();
                ret.addLine(rev, author, true);
            } else {
                LOGGER.log(Level.SEVERE,
                        "Error: did not find annotation in line {0}: [{1}] of {2}",
                        new Object[]{String.valueOf(lineno), line, fileName});
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
        File directory = new File(getDirectoryName());
        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("config");
        cmd.add("--list");

        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        if (executor.getOutputString().contains("remote.origin.url=")) {
            cmd.clear();
            cmd.add(RepoCommand);
            cmd.add("pull");
            cmd.add("-n");
            cmd.add("-q");
            if (executor.exec() != 0) {
                throw new IOException(executor.getErrorString());
            }
        }
    }

    @Override
    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether Git has history
        // available for a file?
        // Otherwise, this is harmless, since Git's commands will just
        // print nothing if there is no history.
        return true;
    }

    @Override
    boolean isRepositoryFor(File file) {
        if (file.isDirectory()) {
            File f = new File(file, ".git");
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
    History getHistory(File file) throws HistoryException {
        return getHistory(file, null);
    }

    @Override
    History getHistory(File file, String sinceRevision)
            throws HistoryException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        History result = new GitHistoryParser().parse(file, this, sinceRevision);
        // Assign tags to changesets they represent
        // We don't need to check if this repository supports tags,
        // because we know it :-)
        if (env.isTagsEnabled()) {
            assignTagsInHistory(result);
        }
        return result;
    }

    @Override
    boolean hasFileBasedTags() {
        return true;
    }

    private TagEntry buildTagEntry(File directory, String tags) throws HistoryException, IOException {
        String hash = null;
        Date date = null;

        ArrayList<String> argv = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("log");
        argv.add("--format=commit:%H" + System.getProperty("line.separator")
                + "Date:%at");
        argv.add("-r");
        argv.add(tags + "^.." + tags);
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(directory);
        Process process;
        process = pb.start();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("commit")) {
                    String parts[] = line.split(":");
                    if (parts.length < 2) {
                        throw new HistoryException("Tag line contains more than 2 columns: " + line);
                    }
                    hash = parts[1];
                }
                if (line.startsWith("Date")) {
                    String parts[] = line.split(":");
                    if (parts.length < 2) {
                        throw new HistoryException("Tag line contains more than 2 columns: " + line);
                    }
                    date = new Date((long) (Integer.parseInt(parts[1])) * 1000);
                }
            }
        }

        try {
            process.exitValue();
        } catch (IllegalThreadStateException e) {
            // the process is still running??? just kill it..
            process.destroy();
        }

        // Git can have tags not pointing to any commit, but tree instead
        // Lets use Unix timestamp of 0 for such commits
        if (date == null) {
            date = new Date(0);
        }
        TagEntry result = new GitTagEntry(hash, date, tags);
        return result;
    }

    @Override
    protected void buildTagList(File directory) {
        this.tagList = new TreeSet<>();
        ArrayList<String> argv = new ArrayList<>();
        LinkedList<String> tagsList = new LinkedList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("tag");
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(directory);
        Process process = null;

        try {
            // First we have to obtain list of all tags, and put it asside
            // Otherwise we can't use git to get date & hash for each tag
            process = pb.start();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    tagsList.add(line);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "Failed to read tag list: {0}", e.getMessage());
            this.tagList = null;
        }

        // Make sure this git instance is not running any more
        if (process != null) {
            try {
                process.exitValue();
            } catch (IllegalThreadStateException e) {
                // the process is still running??? just kill it..
                process.destroy();
            }
        }

        try {
            // Now get hash & date for each tag
            for (String tags : tagsList) {
                TagEntry tagEntry = buildTagEntry(directory, tags);
                // Reverse the order of the list
                this.tagList.add(tagEntry);
            }
        } catch (HistoryException e) {
            LOGGER.log(Level.WARNING,
                    "Failed to parse tag list: {0}", e.getMessage());
            this.tagList = null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "Failed to read tag list: {0}", e.getMessage());
            this.tagList = null;
        }
    }

    @Override
    String determineParent() throws IOException {
        String parent = null;
        File directory = new File(directoryName);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("remote");
        cmd.add("-v");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(directory);
        Process process;
        process = pb.start();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("origin") && line.contains("(fetch)")) {
                    String parts[] = line.split("\\s+");
                    if (parts.length != 3) {
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
    String determineBranch() throws IOException {
        String branch = null;
        File directory = new File(directoryName);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("branch");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(directory);
        Process process;
        process = pb.start();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("*")) {
                    branch = line.substring(2).trim();
                    break;
                }
            }
        }

        return branch;
    }
}
