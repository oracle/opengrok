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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
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
 * Access to a local CVS repository.
 */
public class CVSRepository extends RCSRepository {
    private static final long serialVersionUID = 1L;

    private static ScmChecker cvsBinary = new ScmChecker(new String[]{
                getCommand(), "--version"
            });

    public CVSRepository() {
        type = "CVS";
        datePattern = "yyyy-MM-dd hh:mm:ss";
    }
    
   /**
     * Get the name of the Cvs command that should be used
     * 
     * @return the name of the cvs command in use
     */
    private static String getCommand() {
        return System.getProperty("org.opensolaris.opengrok.history.cvs", "cvs");
    }

    @Override
    public boolean isWorking() {
        return cvsBinary.available;
    }

    @Override
    File getRCSFile(File file) {
        File cvsFile =
                RCSHistoryParser.getCVSFile(file.getParent(), file.getName());
        if (cvsFile != null && cvsFile.exists()) {
            return cvsFile;
        } else {
            return null;
        }
    }

    @Override
    public boolean isRepositoryFor(File file) {
        File cvsDir = new File(file, "CVS");
        return cvsDir.isDirectory();
    }
    
    @Override
    public void update() throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<String>();
        cmd.add(getCommand());
        cmd.add("update");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }

    private Boolean isBranch=null;
    /**
     * Get an executor to be used for retrieving the history log for the
     * named file.
     *
     * @param file The file to retrieve history for
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(final File file) {
        String abs = file.getAbsolutePath();
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }

        List<String> cmd = new ArrayList<String>();
        cmd.add(getCommand());
        cmd.add("log");
        cmd.add("-N"); //don't display tags

        if (isBranch==null) {
            File tagFile = new File(getDirectoryName(), "CVS/Tag");
            if ( tagFile.isFile() ) {isBranch=Boolean.TRUE;}
            else { isBranch=Boolean.FALSE; }
        }
        if (isBranch==Boolean.TRUE) {
            cmd.add("-b"); //just generate THIS branch history, we don't care about the other branches which are not checked out
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
        InputStream in = null;
        String revision = rev;

        if (rev.indexOf(':') != -1) {
            revision = rev.substring(0, rev.indexOf(':'));
        }
        try {
            String argv[] = {getCommand(), "up", "-p", "-r", revision, basename};
            process = Runtime.getRuntime().exec(argv, null, new File(parent));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            in = process.getInputStream();
            int len;

            while ((len = in.read(buffer)) != -1) {
                if (len > 0) {
                    out.write(buffer, 0, len);
                }
            }

            ret = new BufferedInputStream(new ByteArrayInputStream(out.toByteArray()));
        } catch (Exception exp) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, "Failed to get history: " + exp.getClass().toString());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while closing stream", e);
                }
            }
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
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add(getCommand());
        cmd.add("annotate");
        if (revision != null) {
            cmd.add("-r");
            cmd.add(revision);
        }
        cmd.add(file.getName());

        Executor exec = new Executor(cmd, file.getParentFile());
        int status = exec.exec();

        if (status != 0) {
            OpenGrokLogger.getLogger().log(Level.INFO, "Failed to get annotations for: \"" +
                    file.getAbsolutePath() + "\" Exit code: " + status);
        }

        return parseAnnotation(exec.getOutputReader(), file.getName());
    }

    /** Pattern used to extract author/revision from cvs annotate. */
    private final static Pattern ANNOTATE_PATTERN =
        Pattern.compile("([\\.\\d]+)\\W+\\((\\w+)");

    protected Annotation parseAnnotation(Reader input, String fileName) throws IOException {
        BufferedReader in = new BufferedReader(input);
        Annotation ret = new Annotation(fileName);
        String line = "";
        int lineno = 0;
        boolean hasStarted = false;
        Matcher matcher = ANNOTATE_PATTERN.matcher(line);
        while ((line = in.readLine()) != null) {
            // Skip header
            if (!hasStarted && (line.length() == 0 || !Character.isDigit(line.charAt(0)))) {
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
                OpenGrokLogger.getLogger().log(Level.SEVERE, "Error: did not find annotation in line " + lineno + ": [" + line + "]");
            }
        }
        return ret;
    }
}
