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
 * Copyright (c) 2008, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.BufferSink;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.HeadHandler;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.util.Version;

/**
 * Access to a Git repository.
 *
 */
public class GitRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepository.class);

    private static final long serialVersionUID = -6126297612958508386L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY = "org.opengrok.indexer.history.git";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "git";

    /**
     * Git blame command.
     */
    private static final String BLAME = "blame";

    /**
     * Arguments to shorten git IDs.
     */
    private static final int CSET_LEN = 8;
    private static final String ABBREV_LOG = "--abbrev=" + CSET_LEN;
    private static final String ABBREV_BLAME = "--abbrev=" + (CSET_LEN - 1);

    /**
     * All git commands that emit date that needs to be parsed by
     * {@code getDateFormat()} should use this option.
     */
    private static final String GIT_DATE_OPT = "--date=iso8601-strict";

    /**
     * Minimum git version which supports the date format.
     *
     * @see #GIT_DATE_OPT
     */
    private static final Version MINIMUM_VERSION = new Version(2, 1, 2);

    public GitRepository() {
        type = "git";
        /*
         * This should match the 'iso-strict' format used by
         * {@code getHistoryLogExecutor}.
         */
        datePatterns = new String[] {
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        };

        ignoredDirs.add(".git");
        ignoredFiles.add(".git");
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

        String filename = getRepoRelativePath(file);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("log");
        cmd.add("--abbrev-commit");
        cmd.add(ABBREV_LOG);
        cmd.add("--name-only");
        cmd.add("--pretty=fuller");
        cmd.add(GIT_DATE_OPT);
        cmd.add("-m");

        if (file.isFile() && isHandleRenamedFiles()) {
            cmd.add("--follow");
        }

        if (sinceRevision != null) {
            cmd.add(sinceRevision + "..");
        }

        if (filename.length() > 0) {
            cmd.add("--");
            cmd.add(filename);
        }

        return new Executor(cmd, new File(getDirectoryName()), sinceRevision != null);
    }

    Executor getRenamedFilesExecutor(final File file, String sinceRevision) throws IOException {
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        List<String> cmd = new ArrayList<>();
        cmd.add(RepoCommand);
        cmd.add("log");
        cmd.add("--find-renames=8"); // similarity 80%
        cmd.add("--diff-filter=R");
        cmd.add("--summary");
        cmd.add(ABBREV_LOG);
        cmd.add("--name-status");
        cmd.add("--oneline");

        if (file.isFile()) {
            cmd.add("--follow");
        }

        if (sinceRevision != null) {
            cmd.add(sinceRevision + "..");
        }

        String canonicalPath = file.getCanonicalPath();
        if (canonicalPath.length() > getCanonicalDirectoryName().length() + 1) {
            // this is a file in the repository
            cmd.add("--");
            cmd.add(getPathRelativeToCanonicalRepositoryRoot(canonicalPath));
        }

        return new Executor(cmd, new File(getDirectoryName()), sinceRevision != null);
    }

    /**
     * Try to get file contents for given revision.
     *
     * @param sink a required target sink
     * @param fullpath full pathname of the file
     * @param rev revision
     * @return a defined instance with {@code success} == {@code true} if no
     * error occurred and with non-zero {@code iterations} if some data was
     * transferred
     */
    private HistoryRevResult getHistoryRev(
            BufferSink sink, String fullpath, String rev) {

        HistoryRevResult result = new HistoryRevResult();
        File directory = new File(getDirectoryName());
        try {
            /*
             * Be careful, git uses only forward slashes in its command and output (not in file path).
             * Using backslashes together with git show will get empty output and 0 status code.
             */
            String filename = Paths.get(getCanonicalDirectoryName()).relativize(
                    Paths.get(fullpath)).toString().replace(File.separatorChar, '/');
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String[] argv = {
                RepoCommand,
                "show",
                rev + ":" + filename
            };

            Executor executor = new Executor(Arrays.asList(argv), directory,
                    RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
            int status = executor.exec();
            result.iterations = copyBytes(sink, executor.getOutputStream());

            /*
             * If exit value of the process was not 0 then the file did
             * not exist or internal git error occured.
             */
            result.success = (status == 0);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE,
                    String.format(
                            "Failed to get history for file %s in revision %s:",
                            fullpath, rev
                    ),
                    exception
            );
        }
        return result;
    }

    @Override
    boolean getHistoryGet(
            BufferSink sink, String parent, String basename, String rev) {

        String fullpath;
        try {
            fullpath = new File(parent, basename).getCanonicalPath();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> String.format(
                    "Failed to get canonical path: %s/%s", parent, basename));
            return false;
        }

        HistoryRevResult result = getHistoryRev(sink::write, fullpath, rev);
        if (!result.success && result.iterations < 1) {
            /*
             * If we failed to get the contents it might be that the file was
             * renamed so we need to find its original name in that revision
             * and retry with the original name.
             */
            String origpath;
            try {
                origpath = findOriginalName(fullpath, rev);
            } catch (IOException exp) {
                LOGGER.log(Level.SEVERE, exp, () -> String.format(
                        "Failed to get original revision: %s/%s (revision %s)",
                        parent, basename, rev));
                return false;
            }

            if (origpath != null) {
                String fullRenamedPath;
                try {
                    fullRenamedPath = Paths.get(getCanonicalDirectoryName(), origpath).toString();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e, () -> String.format(
                            "Failed to get canonical path: .../%s", origpath));
                    return false;
                }
                if (!fullRenamedPath.equals(fullpath)) {
                    result = getHistoryRev(sink, fullRenamedPath, rev);
                }
            }
        }

        return result.success;
    }

    /**
     * Create a {@code Reader} that reads an {@code InputStream} using the
     * correct character encoding.
     *
     * @param input a stream with the output from a log or blame command
     * @return a reader that reads the input
     */
    static Reader newLogReader(InputStream input) {
        // Bug #17731: Git always encodes the log output using UTF-8 (unless
        // overridden by i18n.logoutputencoding, but let's assume that hasn't
        // been done for now). Create a reader that uses UTF-8 instead of the
        // platform's default encoding.
        return new InputStreamReader(input, StandardCharsets.UTF_8);
    }

    private String getPathRelativeToCanonicalRepositoryRoot(String fullpath)
            throws IOException {
        String repoPath = getCanonicalDirectoryName() + File.separator;
        if (fullpath.startsWith(repoPath)) {
            return fullpath.substring(repoPath.length());
        }
        return fullpath;
    }

    /**
     * Get the name of file in given revision. The returned file name is relative
     * to the repository root.
     *
     * @param fullpath  file path
     * @param changeset changeset
     * @return original filename relative to the repository root
     * @throws java.io.IOException if I/O exception occurred
     * @see #getPathRelativeToCanonicalRepositoryRoot(String)
     */
    String findOriginalName(String fullpath, String changeset)
            throws IOException {
        if (fullpath == null || fullpath.isEmpty()) {
            throw new IOException(String.format("Invalid file path string: %s", fullpath));
        }

        if (changeset == null || changeset.isEmpty()) {
            throw new IOException(String.format("Invalid changeset string for path %s: %s",
                    fullpath, changeset));
        }

        String fileInRepo = getPathRelativeToCanonicalRepositoryRoot(fullpath);

        /*
         * Get the list of file renames for given file to the specified
         * revision.
         */
        String[] argv = {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK),
            "log",
            "--follow",
            "--summary",
            ABBREV_LOG,
            "--abbrev-commit",
            "--name-status",
            "--pretty=format:commit %h%n%d",
            "--",
            fileInRepo
        };

        Executor executor = new Executor(Arrays.asList(argv), new File(getDirectoryName()),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        int status = executor.exec();

        String originalFile = null;
        try (BufferedReader in = new BufferedReader(newLogReader(executor.getOutputStream()))) {
            String line;
            String rev = null;
            Matcher m;
            Pattern pattern = Pattern.compile("^R\\d+\\s(.*)\\s(.*)");
            while ((line = in.readLine()) != null) {
                if (line.startsWith("commit ")) {
                    rev = line.substring(7);
                    continue;
                }

                if (changeset.equals(rev)) {
                    if (originalFile == null) {
                        originalFile = fileInRepo;
                    }
                    break;
                }

                if ((m = pattern.matcher(line)).find()) {
                    // git output paths with forward slashes so transform it if needed
                    originalFile = Paths.get(m.group(1)).toString();
                }
            }
        }

        if (status != 0 || originalFile == null) {
            LOGGER.log(Level.WARNING,
                    "Failed to get original name in revision {2} for: \"{0}\" Exit code: {1}",
                    new Object[]{fullpath, String.valueOf(status), changeset});
            return null;
        }

        return originalFile;
    }

    /**
     * Get first revision of given file without following renames.
     * @param fullpath file path to get first revision of
     */
    private String getFirstRevision(String fullpath) {
        String[] argv = {
                ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK),
                "rev-list",
                "--reverse",
                "HEAD",
                "--",
                fullpath
        };

        Executor executor = new Executor(Arrays.asList(argv), new File(getDirectoryName()),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        HeadHandler headHandler = new HeadHandler(1);
        int status = executor.exec(false, headHandler);

        String line;
        if (headHandler.count() > 0 && (line = headHandler.get(0)) != null) {
            return line.trim();
        }

        LOGGER.log(Level.WARNING,
                "Failed to get first revision for: \"{0}\" Exit code: {1}",
                new Object[]{fullpath, String.valueOf(status)});

        return null;
    }

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation or {@code null}
     * @throws java.io.IOException if I/O exception occurred
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
        } else {
            // {@code git blame} follows renames by default. If renamed file handling is off, its output would
            // contain invalid revisions. Therefore, the revision range needs to be constrained.
            if (!isHandleRenamedFiles()) {
                String firstRevision = getFirstRevision(file.getAbsolutePath());
                if (firstRevision == null) {
                    return null;
                }
                cmd.add(firstRevision + "..");
            }
        }
        cmd.add("--");
        cmd.add(getPathRelativeToCanonicalRepositoryRoot(file.getCanonicalPath()));

        Executor executor = new Executor(cmd, new File(getDirectoryName()),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        GitAnnotationParser parser = new GitAnnotationParser(file.getName());
        int status = executor.exec(true, parser);

        // File might have changed its location if it was renamed.
        // Try to lookup its original name and get the annotation again.
        if (status != 0 && isHandleRenamedFiles()) {
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
            cmd.add(findOriginalName(file.getCanonicalPath(), revision));
            executor = new Executor(cmd, new File(getDirectoryName()),
                    RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
            parser = new GitAnnotationParser(file.getName());
            status = executor.exec(true, parser);
        }

        if (status != 0) {
            LOGGER.log(Level.WARNING,
                    "Failed to get annotations for: \"{0}\" Exit code: {1}",
                    new Object[]{file.getAbsolutePath(), String.valueOf(status)});
            return null;
        }

        return parser.getAnnotation();
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
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
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        if (file.isDirectory()) {
            File f = new File(file, ".git");
            return f.exists();
        }
        return false;
    }

    @Override
    boolean supportsSubRepositories() {
        return true;
    }

    /**
     * Gets a value indicating the instance is nestable.
     * @return {@code true}
     */
    @Override
    boolean isNestable() {
        return true;
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            Executor exec = new Executor(new String[]{RepoCommand, "--version"});

            if (exec.exec(false) == 0) {
                final String outputVersion = exec.getOutputString();
                final String version = outputVersion.replaceAll(".*? version (\\d+(\\.\\d+)*).*", "$1");
                try {
                    working = Version.from(version).compareTo(MINIMUM_VERSION) >= 0;
                } catch (NumberFormatException ex) {
                    LOGGER.log(Level.WARNING, String.format("Unable to detect git version from %s", outputVersion), ex);
                    working = false;
                }
            } else {
                working = false;
            }
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
        History result = new GitHistoryParser(isHandleRenamedFiles()).parse(file, this, sinceRevision);
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

    /**
     * Builds a Git tag list by querying Git commit hash, commit time, and tag
     * names.
     * <p>Repository technically relies on the tag list to be ancestor ordered.
     * <p>For a version control system that uses "linear revision numbering"
     * (e.g. Subversion or Mercurial), the natural ordering in the
     * {@link TreeSet} is by ancestor order and so
     * {@link TagEntry#compareTo(HistoryEntry)} always determines the correct
     * tag.
     * <p>For {@link GitTagEntry} that does not use linear revision numbering,
     * the {@link TreeSet} will be ordered by date. That does not necessarily
     * align with ancestor order. In that case,
     * {@link GitTagEntry#compareTo(HistoryEntry)} that compares by date can
     * find the wrong tag.
     * <p>Linus Torvalds: [When asking] "'can commit X be an ancestor of commit
     * Y' (as a way to basically limit certain algorithms from having to walk
     * all the way down). We've used commit dates for it, and realistically it
     * really has worked very well. But it was always a broken heuristic."
     * <p>"I think the lack of [generation numbers] is literally the only real
     * design mistake we have [in Git]."
     * <p>"We discussed adding generation numbers about 6 years ago [in 2005].
     * We clearly *should* have done it. Instead, we went with the hacky `let's
     * use commit time', that everybody really knew was technically wrong, and
     * was a hack, but avoided the need."
     * <p>If Git ever gets standard generation numbers,
     * {@link GitTagEntry#compareTo(HistoryEntry)} should be revised to work
     * reliably in all cases akin to a version control system that uses "linear
     * revision numbering."
     * @param directory a defined directory of the repository
     * @param cmdType command timeout type
     */
    @Override
    protected void buildTagList(File directory, CommandTimeoutType cmdType) {
        this.tagList = new TreeSet<>();

        /*
         * Bulk log-tags command courtesy of GitHub's louie0817.
         */
        ArrayList<String> argv = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("log");
        argv.add("--tags");
        argv.add("--simplify-by-decoration");
        argv.add("--pretty=%H:%at:%D:");

        Executor executor = new Executor(argv, directory,
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        int status = executor.exec(true, new GitTagParser(this.tagList));
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Read tags count={0} for {1}",
                    new Object[] {tagList.size(), directory});
        }

        if (status != 0) {
            LOGGER.log(Level.WARNING,
                "Failed to get tags for: \"{0}\" Exit code: {1}",
                    new Object[]{directory.getAbsolutePath(), String.valueOf(status)});
            // In case of partial success, do not null-out tagList here.
        }
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        String parent = null;
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("remote");
        cmd.add("-v");
        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        executor.exec();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(executor.getOutputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("origin") && line.contains("(fetch)")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length != 3) {
                        LOGGER.log(Level.WARNING,
                                "Failed to get parent for {0}", getDirectoryName());
                    }
                    parent = parts[1];
                    break;
                }
            }
        }

        return parent;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) throws IOException {
        String branch = null;
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("branch");
        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        executor.exec();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(executor.getOutputStream()))) {
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

    @Override
    public String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        File directory = new File(getDirectoryName());
        List<String> cmd = new ArrayList<>();
        // The delimiter must not be contained in the date format emitted by
        // {@code GIT_DATE_OPT}.
        String delim = "#";

        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);

        cmd.add(RepoCommand);
        cmd.add("log");
        cmd.add("-1");
        cmd.add("--pretty=%cd" + delim + "%h %an %s");
        cmd.add(GIT_DATE_OPT);

        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        if (executor.exec(false) != 0) {
            throw new IOException(executor.getErrorString());
        }

        String output = executor.getOutputString().trim();
        int indexOf = StringUtils.nthIndexOf(output, delim, 1);
        if (indexOf < 0) {
            throw new IOException(
                    String.format("Couldn't extract date from \"%s\".", output));
        }

        try {
            Date date = parse(output.substring(0, indexOf));
            return String.format("%s %s", format(date), output.substring(indexOf + 1));
        } catch (ParseException ex) {
            throw new IOException(ex);
        }
    }
}
