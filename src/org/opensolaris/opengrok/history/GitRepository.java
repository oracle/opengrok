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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved. 
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.Executor;

/**
 * Access to a Git repository.
 * 
 */
public class GitRepository extends Repository {
    private static final long serialVersionUID = 1L;

    private static ScmChecker gitBinary = new ScmChecker(new String[] {
        getCommand(), "--help" });

    public GitRepository() {
        type = "git";
        datePattern = "EEE MMM dd hh:mm:ss yyyy ZZZZ";
    }

   /**
     * Get an executor to be used for retrieving the history log for the
     * named file.
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
        
        List<String> cmd = new ArrayList<String>();
        cmd.add(getCommand());
        cmd.add("log");
        cmd.add("--name-only");
        cmd.add("--pretty=fuller");

        if (filename.length() > 0) {
           cmd.add(filename);
       }
        return new Executor(cmd, new File(getDirectoryName()));
    }    
    
    /**
     * Get the name of the Git command that should be used
     * @return the name of the hg command in use
     */
    private static String getCommand() {
        return System.getProperty("org.opensolaris.opengrok.history.git", "git");
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        File directory = new File(directoryName);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        
        Process process = null;
        try {
            String filename =  (new File(parent, basename)).getCanonicalPath().substring(directoryName.length() + 1);
            String argv[] = {getCommand(), "show", rev + ":" + filename};
            process = Runtime.getRuntime().exec(argv, null, directory);
            
            InputStream in = process.getInputStream();
            int len;
            
            while ((len = in.read(buffer)) != -1) {
                if (len > 0) {
                    output.write(buffer, 0, len);
                }
            }
            
            ret = new BufferedInputStream(new ByteArrayInputStream(output.toByteArray()));
        } catch (Exception exp) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to get history: " + exp.getClass().toString(), exp);
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

    /** Pattern used to extract author/revision from git blame. */
    private final static Pattern BLAME_PATTERN =
        Pattern.compile("^\\W*(\\w+).+?\\((\\D+).*$");

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        List<String> cmd = new ArrayList<String>();
        cmd.add(getCommand());
        cmd.add("blame");
        cmd.add("-l");
        if (revision != null) {
            cmd.add(revision);
        }
        cmd.add(file.getName());

        Executor exec = new Executor(cmd, file.getParentFile());
        int status = exec.exec();
        
        if (status != 0) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to get annotations for: \"{0}\" Exit code: {1}", new Object[]{file.getAbsolutePath(), String.valueOf(status)});
        }

        return parseAnnotation(exec.getOutputReader(), file.getName());
    }

    protected Annotation parseAnnotation(Reader input, String fileName) throws IOException {
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
                OpenGrokLogger.getLogger().log(Level.SEVERE, "Error: did not find annotation in line {0}: [{1}] of {2}", new Object[]{String.valueOf(lineno), line, fileName});
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
        List<String> cmd = new ArrayList<String>();
        cmd.add(getCommand());
        cmd.add("config");
        cmd.add("--list");

        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }

        if (executor.getOutputString().indexOf("remote.origin.url=") != -1) {
            cmd.clear();
            cmd.add(getCommand());
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
       } else {
        return false; }
    }

    @Override
    public boolean isWorking() {
        return gitBinary.available;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new GitHistoryParser().parse(file, this);
    }
}

