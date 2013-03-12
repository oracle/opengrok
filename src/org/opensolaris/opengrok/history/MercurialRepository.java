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
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.web.Util;

/**
 * Access to a Mercurial repository.
 *
 */
public class MercurialRepository extends Repository {
    private static final long serialVersionUID = 1L;

    /** The property name used to obtain the client command for thisrepository. */
    public static final String CMD_PROPERTY_KEY =
        "org.opensolaris.opengrok.history.Mercurial";
    /** The command to use to access the repository if none was given explicitly */
    public static final String CMD_FALLBACK = "hg";

    /**
     * The boolean property and environment variable name to indicate
     * whether forest-extension in Mercurial adds repositories inside the
     * repositories.
     */
    public static final String NOFOREST_PROPERTY_KEY =
        "org.opensolaris.opengrok.history.mercurial.disableForest";

    /** Template for formatting hg log output for files. */
    private static final String TEMPLATE = "changeset: {rev}:{node|short}\\n"
        + "{branches}{tags}{parents}\\n"
        + "user: {author}\\ndate: {date|isodate}\\n"
        + "description: {desc|strip|obfuscate}\\n";

    /** Template for formatting hg log output for directories. */
    private static final String DIR_TEMPLATE = TEMPLATE
        + "files: {files}{file_copies}\\n";

    public MercurialRepository() {
        type = "Mercurial";
        datePattern = "yyyy-MM-dd hh:mm ZZZZ";
    }

    /**
     * Get an executor to be used for retrieving the history log for the
     * named file.
     *
     * @param file The file to retrieve history for
     * @param changeset the oldest changeset to return from the executor,
     * or {@code null} if all changesets should be returned
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(File file, String changeset)
             throws HistoryException, IOException
    {
        String abs = file.getCanonicalPath();
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("log");
        if (!file.isDirectory()) { cmd.add("-f"); }

        if (changeset != null) {
            cmd.add("-r");
            String[] parts = changeset.split(":");
            if (parts.length == 2) {
                cmd.add("tip:" + parts[0]);
            } else {
                throw new HistoryException(
                        "Don't know how to parse changeset identifier: " +
                        changeset);
            }
        }

        cmd.add("--template");
        cmd.add(file.isDirectory() ? DIR_TEMPLATE : TEMPLATE);
        cmd.add(filename);

        return new Executor(cmd, new File(directoryName));
    }

    /**
     * Try to get file contents for given revision.
     * 
     * @param fullpath full pathname of the file
     * @param rev revision
     * @return contents of the file in revision rev
     */
    private InputStream getHistoryRev(String fullpath, String rev)
    {
        InputStream ret = null;

        File directory = new File(directoryName);

        Process process = null;
        String revision = rev;

        if (rev.indexOf(':') != -1) {
            revision = rev.substring(0, rev.indexOf(':'));
        }
        try {
            String filename = 
                fullpath.substring(directoryName.length() + 1);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String argv[] = {cmd, "cat", "-r", revision, filename};
            process = Runtime.getRuntime().exec(argv, null, directory);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            int total_len = 0;
            try (InputStream in = process.getInputStream()) {
                int len;

                while ((len = in.read(buffer)) != -1) {
                    if (len > 0) {
                        out.write(buffer, 0, len);
                        total_len += len;
                    }
                }
            }

            if (total_len > 0)
                ret = new ByteArrayInputStream(out.toByteArray());
            else
                ret = null;
        } catch (Exception exp) {
            OpenGrokLogger.getLogger().log(Level.SEVERE,
                "Failed to get history: " + exp.getClass().toString());
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

    private static final Pattern LOG_COPIES_PATTERN =
        Pattern.compile("^(\\d+):(.*)");
    
    /**
     * Get the name of file in revision rev
     * @param fullpath file path
     * @param rev_to_find revision number
     * @returns original filename
     */
    private String FindOriginalName(String fullpath, String rev_to_find) 
            throws IOException {
        Matcher matcher = LOG_COPIES_PATTERN.matcher("");
        String file = fullpath.substring(directoryName.length() + 1);
        ArrayList<String> argv = new ArrayList<String>();
        
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(cmd);
        argv.add("log");
        argv.add("-f");
        argv.add("--template");
        argv.add("{rev}:{file_copies}\\n");
        argv.add(fullpath);
        
        ProcessBuilder pb = new ProcessBuilder(argv);
        Process process = null;
        
        try {
           process = pb.start();
           try (BufferedReader in = new BufferedReader(
                   new InputStreamReader(process.getInputStream()))) {
               String line;
               while ((line = in.readLine()) != null) {
                    matcher.reset(line);
                    if (!matcher.find()) {
                        // XXX print error
                        return (null);
                    }
                    String rev = matcher.group(1);
                    String content = matcher.group(2);

                    if (!content.isEmpty()) {
                        String[] splitArray = content.split("\\)");
                        for (String s: splitArray) {
                            /*
                             * Choose a value which is not probable
                             * to form a substring of the file names.
                             */
                            String splitter = "-opengroksplitter-";
                            s = s.replace(" (", splitter);
                            String[] move = s.split(splitter);

                            if (file.equals(move[0])) {
                                file = move[1];
                                break;
                            }
                        }
                    }

                    if (rev.equals(rev_to_find))
                        break;
               }
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
        
        return (fullpath.substring(0, directoryName.length() + 1) + file);
    }
    
    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev)
    {
       InputStream ret;
       String fullpath;
       
       try {
           fullpath = new File(parent, basename).getCanonicalPath();
       } catch (IOException exp) {
           OpenGrokLogger.getLogger().log(Level.SEVERE,
               "Failed to get canonical path: {0}", exp.getClass().toString());
           return null;
       }

       ret = getHistoryRev(fullpath, rev);
       if (ret == null) {
           /*
            * If we failed to get the contents it might be that the file was
            * renamed so we need to find its original name in that revision
            * and retry with the original name.
            */
           String origpath;
           try {
            origpath = FindOriginalName(fullpath, rev);
           } catch (IOException exp) {
               OpenGrokLogger.getLogger().log(Level.SEVERE,
                "Failed to get original revision: {0}",
                exp.getClass().toString());
               return null;
           }
           if (origpath != null)
               ret = getHistoryRev(origpath, rev);
       }
       
       return ret;
    }
    
    /** Pattern used to extract author/revision from hg annotate. */
    private static final Pattern ANNOTATION_PATTERN =
        Pattern.compile("^\\s*(\\d+):");

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        ArrayList<String> argv = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(cmd);
        argv.add("annotate");
        argv.add("-n");
        if (revision != null) {
            argv.add("-r");
            if (revision.indexOf(':') == -1) {
                argv.add(revision);
            } else {
                argv.add(revision.substring(0, revision.indexOf(':')));
            }
        }
        argv.add(file.getName());
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(file.getParentFile());
        Process process = null;
        Annotation ret = null;
        HashMap<String,HistoryEntry> revs = new HashMap<String,HistoryEntry>();

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
            OpenGrokLogger.getLogger().log(Level.SEVERE,
                "Error: cannot get history for file " + file);
            return null;
        }

        try {
            process = pb.start();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                ret = new Annotation(file.getName());
                String line;
                int lineno = 0;
                Matcher matcher = ANNOTATION_PATTERN.matcher("");
                while ((line = in.readLine()) != null) {
                    ++lineno;
                    matcher.reset(line);
                    if (matcher.find()) {
                        String rev = matcher.group(1);
                        String author = "N/A";
                        // Use the history index hash map to get the author.
                        if (revs.get(rev) != null) {
                             author = revs.get(rev).getAuthor();
                        }
                        ret.addLine(rev, Util.getEmail(author.trim()), true);
                    } else {
                        OpenGrokLogger.getLogger().log(Level.SEVERE,
                            "Error: did not find annotation in line "
                            + lineno + ": [" + line + "]");
                    }
                }
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
    public void update() throws IOException {
        File directory = new File(directoryName);

        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("showconfig");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        if (executor.getOutputString().indexOf("paths.default=") != -1) {
            cmd.clear();
            cmd.add(this.cmd);
            cmd.add("pull");
            cmd.add("-u");
            executor = new Executor(cmd, directory);
            if (executor.exec() != 0) {
                throw new IOException(executor.getErrorString());
            }
        }
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
    boolean isRepositoryFor(File file) {
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

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(cmd);
        }
        return working.booleanValue();
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
        History result = new MercurialHistoryParser(this).parse(file, sinceRevision);
        // Assign tags to changesets they represent
        // We don't need to check if this repository supports tags, because we know it:-)
        if (env.isTagsEnabled()) {
            assignTagsInHistory(result);
        }
        return result;
    }
    
    /**
     * We need to create list of all tags prior to creation of HistoryEntries
     * per file.
     * @return true.
     */
    @Override
    boolean hasFileBasedTags() {
        return true;
    }
    
    @Override
    protected void buildTagList(File directory) {
        this.tagList = new TreeSet<TagEntry>();
        ArrayList<String> argv = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(cmd);
        argv.add("tags");
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(directory);
        Process process = null;

        try {
            process = pb.start();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    String parts[] = line.split("  *");
                    if (parts.length < 2) {
                        throw new HistoryException("Tag line contains more than 2 columns: " + line);
                    }
                    // Grrr, how to parse tags with spaces inside?
                    // This solution will loose multiple spaces;-/
                    String tag = parts[0];
                    for (int i = 1; i < parts.length - 1; ++i) {
                        tag += " " + parts[i];
                    }
                    String revParts[] = parts[parts.length - 1].split(":");
                    if (revParts.length != 2) {
                        throw new HistoryException("Mercurial revision parsing error: " + parts[parts.length - 1]);
                    }
                    TagEntry tagEntry = new MercurialTagEntry(Integer.parseInt(revParts[0]), tag);
                    // Reverse the order of the list
                    this.tagList.add(tagEntry);
                }
            }
        } catch (IOException e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to read tag list: {0}", e.getMessage());
            this.tagList = null;
        } catch (HistoryException e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to parse tag list: {0}", e.getMessage());
            this.tagList = null;
        }
        
        if (process != null) {
            try {
                process.exitValue();
            } catch (IllegalThreadStateException e) {
                // the process is still running??? just kill it..
                process.destroy();
            }
        }
    }
}
