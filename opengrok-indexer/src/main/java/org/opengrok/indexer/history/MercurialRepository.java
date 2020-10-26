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
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

/**
 * Access to a Mercurial repository.
 *
 */
public class MercurialRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(MercurialRepository.class);

    private static final long serialVersionUID = 1L;

    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY = "org.opengrok.indexer.history.Mercurial";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "hg";

    /**
     * The boolean property and environment variable name to indicate whether
     * forest-extension in Mercurial adds repositories inside the repositories.
     */
    public static final String NOFOREST_PROPERTY_KEY
            = "org.opengrok.indexer.history.mercurial.disableForest";

    static final String CHANGESET = "changeset: ";
    static final String USER = "user: ";
    static final String DATE = "date: ";
    static final String DESCRIPTION = "description: ";
    static final String FILE_COPIES = "file_copies: ";
    static final String FILES = "files: ";
    static final String END_OF_ENTRY
            = "mercurial_history_end_of_entry";

    private static final String TEMPLATE_STUB
            = CHANGESET + "{rev}:{node|short}\\n"
            + USER + "{author}\\n" + DATE + "{date|isodate}\\n"
            + DESCRIPTION + "{desc|strip|obfuscate}\\n";

    private static final String FILE_LIST = FILES + "{files}\\n";

    /**
     * Templates for formatting hg log output for files.
     */
    private static final String FILE_TEMPLATE = TEMPLATE_STUB
            + END_OF_ENTRY + "\\n";

    /**
     * Template for formatting {@code hg log} output for directories.
     */
    private static final String DIR_TEMPLATE_RENAMED
            = TEMPLATE_STUB + FILE_LIST
            + FILE_COPIES + "{file_copies}\\n" + END_OF_ENTRY + "\\n";
    private static final String DIR_TEMPLATE
            = TEMPLATE_STUB + FILE_LIST
            + END_OF_ENTRY + "\\n";

    private static final Pattern LOG_COPIES_PATTERN
            = Pattern.compile("^(\\d+):(.*)");

    public MercurialRepository() {
        type = "Mercurial";
        datePatterns = new String[]{
            "yyyy-MM-dd hh:mm ZZZZ"
        };

        ignoredFiles.add(".hgtags");
        ignoredFiles.add(".hgignore");
        ignoredDirs.add(".hg");
    }

    /**
     * Return name of the branch or "default".
     */
    @Override
    String determineBranch(CommandTimeoutType cmdType) throws IOException {
        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("branch");

        Executor executor = new Executor(cmd, new File(getDirectoryName()),
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        if (executor.exec(false) != 0) {
            throw new IOException(executor.getErrorString());
        }

        return executor.getOutputString().trim();
    }

    /**
     * Get an executor to be used for retrieving the history log for the named
     * file or directory.
     *
     * @param file The file or directory to retrieve history for
     * @param sinceRevision the oldest changeset to return from the executor, or
     *                  {@code null} if all changesets should be returned.
     *                  For files this does not apply and full history is returned.
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(File file, String sinceRevision)
            throws HistoryException, IOException {
        String filename = getRepoRelativePath(file);
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("log");

        if (file.isDirectory()) {
            // If this is non-default branch we would like to get the changesets
            // on that branch and also follow any changesets from the parent branch.
            if (sinceRevision != null) {
                cmd.add("-r");
                String[] parts = sinceRevision.split(":");
                if (parts.length == 2) {
                    cmd.add("reverse(" + parts[0] + "::'" + getBranch() + "')");
                } else {
                    throw new HistoryException(
                            "Don't know how to parse changeset identifier: "
                            + sinceRevision);
                }
            } else {
                cmd.add("-r");
                cmd.add("reverse(0::'" + getBranch() + "')");
            }
        } else {
            // For plain files we would like to follow the complete history
            // (this is necessary for getting the original name in given revision
            // when handling renamed files)
            // It is not needed to filter on a branch as 'hg log' will follow
            // the active branch.
            // Due to behavior of recent Mercurial versions, it is not possible
            // to filter the changesets of a file based on revision.
            // For files this does not matter since if getHistory() is called
            // for a file, the file has to be renamed so we want its complete history
            // if renamed file handling is enabled for this repository.
            if (this.isHandleRenamedFiles()) {
                cmd.add("--follow");
            }
        }

        cmd.add("--template");
        if (file.isDirectory()) {
            cmd.add(this.isHandleRenamedFiles() ? DIR_TEMPLATE_RENAMED : DIR_TEMPLATE);
        } else {
            cmd.add(FILE_TEMPLATE);
        }

        if (!filename.isEmpty()) {
            cmd.add("--");
            cmd.add(filename);
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

        String revision = rev;
        if (rev.indexOf(':') != -1) {
            revision = rev.substring(0, rev.indexOf(':'));
        }
        try {
            String filename
                    = fullpath.substring(getDirectoryName().length() + 1);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String[] argv = {RepoCommand, "cat", "-r", revision, filename};
            Executor executor = new Executor(Arrays.asList(argv), directory,
                    RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
            int status = executor.exec();
            result.iterations = copyBytes(sink, executor.getOutputStream());

            /*
             * If exit value of the process was not 0 then the file did
             * not exist or internal hg error occured.
             */
            result.success = (status == 0);
        } catch (Exception exp) {
            LOGGER.log(Level.SEVERE, "Failed to get history", exp);
        }
        return result;
    }

    /**
     * Get the name of file in given revision. This is used to get contents
     * of a file in historical revision.
     *
     * @param fullpath file path
     * @param full_rev_to_find revision number (in the form of
     * {rev}:{node|short})
     * @returns original filename
     */
    private String findOriginalName(String fullpath, String full_rev_to_find)
            throws IOException {
        Matcher matcher = LOG_COPIES_PATTERN.matcher("");
        String file = fullpath.substring(getDirectoryName().length() + 1);
        ArrayList<String> argv = new ArrayList<>();
        File directory = new File(getDirectoryName());

        // Extract {rev} from the full revision specification string.
        String[] rev_array = full_rev_to_find.split(":");
        String rev_to_find = rev_array[0];
        if (rev_to_find.isEmpty()) {
            LOGGER.log(Level.SEVERE,
                    "Invalid revision string: {0}", full_rev_to_find);
            return null;
        }

        /*
         * Get the list of file renames for given file to the specified
         * revision. We need to get them from the newest to the oldest
         * so that we can follow the renames down to the revision we are after.
         */
        argv.add(ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK));
        argv.add("log");
        argv.add("--follow");
        /*
         * hg log --follow -r behavior has changed since Mercurial 3.4
         * so filtering the changesets of a file no longer works with --follow.
         * This is tracked by https://bz.mercurial-scm.org/show_bug.cgi?id=4959
         * Once this is fixed and Mercurial versions with the fix are prevalent,
         * we can revert to the old behavior.
         */
        // argv.add("-r");
        // Use reverse() to get the changesets from newest to oldest. 
        // argv.add("reverse(" + rev_to_find + ":)");
        argv.add("--template");
        argv.add("{rev}:{file_copies}\\n");
        argv.add(fullpath);

        Executor executor = new Executor(argv, directory,
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        int status = executor.exec();

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(executor.getOutputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                matcher.reset(line);
                if (!matcher.find()) {
                    LOGGER.log(Level.SEVERE,
                            "Failed to match: {0}", line);
                    return (null);
                }
                String rev = matcher.group(1);
                String content = matcher.group(2);

                if (rev.equals(rev_to_find)) {
                    break;
                }

                if (!content.isEmpty()) {
                    /*
                     * Split string of 'newfile1 (oldfile1)newfile2 (oldfile2) ...' into pairs of renames.
                     */
                    String[] splitArray = content.split("\\)");
                    for (String s : splitArray) {
                        /*
                         * This will fail for file names containing ' ('.
                         */
                        String[] move = s.split(" \\(");

                        if (file.equals(move[0])) {
                            file = move[1];
                            break;
                        }
                    }
                }
            }
        }

        if (status != 0) {
            LOGGER.log(Level.WARNING,
                    "Failed to get original name in revision {2} for: \"{0}\" Exit code: {1}",
                    new Object[]{fullpath, String.valueOf(status), full_rev_to_find});
            return null;
        }

        return (fullpath.substring(0, getDirectoryName().length() + 1) + file);
    }

    @Override
    boolean getHistoryGet(
            BufferSink sink, String parent, String basename, String rev) {

        String fullpath;
        try {
            fullpath = new File(parent, basename).getCanonicalPath();
        } catch (IOException exp) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get canonical path: {0}", exp.getClass().toString());
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
                LOGGER.log(Level.SEVERE,
                        "Failed to get original revision: {0}",
                        exp.getClass().toString());
                return false;
            }
            if (origpath != null && !origpath.equals(fullpath)) {
                result = getHistoryRev(sink, origpath, rev);
            }
        }

        return result.success;
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
        ArrayList<String> argv = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("annotate");
        argv.add("-n");
        if (!this.isHandleRenamedFiles()) {
            argv.add("--no-follow");
        }
        if (revision != null) {
            argv.add("-r");
            if (revision.indexOf(':') == -1) {
                argv.add(revision);
            } else {
                argv.add(revision.substring(0, revision.indexOf(':')));
            }
        }
        argv.add(file.getName());
        Executor executor = new Executor(argv, file.getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        HashMap<String, HistoryEntry> revs = new HashMap<>();

        // Construct hash map for history entries from history cache. This is
        // needed later to get user string for particular revision.
        try {
            History hist = HistoryGuru.getInstance().getHistory(file, false);
            for (HistoryEntry e : hist.getHistoryEntries()) {
                // Chop out the colon and all hexadecimal what follows.
                // This is because the whole changeset identification is
                // stored in history index while annotate only needs the
                // revision identifier.
                revs.put(e.getRevision().replaceFirst(":[a-f0-9]+", ""), e);
            }
        } catch (HistoryException he) {
            LOGGER.log(Level.SEVERE,
                    "Error: cannot get history for file {0}", file);
            return null;
        }

        MercurialAnnotationParser annotator = new MercurialAnnotationParser(file, revs);
        executor.exec(true, annotator);

        return annotator.getAnnotation();
    }

    @Override
    protected String getRevisionForAnnotate(String history_revision) {
        String[] brev = history_revision.split(":");

        return brev[0];
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    @Override
    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether mercurial has history
        // available for a file?
        // Otherwise, this is harmless, since mercurial's commands will just
        // print nothing if there is no history.
        return true;
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        if (file.isDirectory()) {
            File f = new File(file, ".hg");
            return f.exists() && f.isDirectory();
        }
        return false;
    }

    @Override
    boolean supportsSubRepositories() {
        String val = System.getenv(NOFOREST_PROPERTY_KEY);
        return !(val == null
                ? Boolean.getBoolean(NOFOREST_PROPERTY_KEY)
                : Boolean.parseBoolean(val));
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
            working = checkCmd(RepoCommand);
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
        // Note that the filtering of revisions based on sinceRevision is done
        // in the history log executor by passing appropriate options to
        // the 'hg' executable.
        // This is done only for directories since if getHistory() is used
        // for file, the file is renamed and its complete history is fetched
        // so no sinceRevision filter is needed.
        // See findOriginalName() code for more details.
        History result = new MercurialHistoryParser(this).parse(file,
                sinceRevision);
        
        // Assign tags to changesets they represent.
        // We don't need to check if this repository supports tags,
        // because we know it :-)
        if (env.isTagsEnabled()) {
            assignTagsInHistory(result);
        }
        return result;
    }

    /**
     * We need to create list of all tags prior to creation of HistoryEntries
     * per file.
     *
     * @return true.
     */
    @Override
    boolean hasFileBasedTags() {
        return true;
    }

    @Override
    protected void buildTagList(File directory, CommandTimeoutType cmdType) {
        this.tagList = new TreeSet<>();
        ArrayList<String> argv = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("tags");

        Executor executor = new Executor(argv, directory,
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        MercurialTagParser parser = new MercurialTagParser();
        int status = executor.exec(true, parser);
        if (status != 0) {
            LOGGER.log(Level.WARNING,
                    "Failed to get tags for: \"{0}\" Exit code: {1}",
                    new Object[]{directory.getAbsolutePath(), String.valueOf(status)});
        } else {
            this.tagList = parser.getEntries();
        }
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("paths");
        cmd.add("default");
        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        if (executor.exec(false) != 0) {
            throw new IOException(executor.getErrorString());
        }

        return executor.getOutputString().trim();
    }

    @Override
    public String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        String line = null;
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("log");
        cmd.add("-l");
        cmd.add("1");
        cmd.add("--template");
        cmd.add("{date|isodate} {node|short} {author} {desc|strip}");

        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        if (executor.exec(false) != 0) {
            throw new IOException(executor.getErrorString());
        }

        return executor.getOutputString().trim();
    }
}
