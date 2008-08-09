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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class gives access to repositories built on top of SCCS (including
 * TeamWare).
 */
public class SCCSRepository extends Repository {
    private boolean verbose;
    private Map<String, String> authors_cache;
    
    /**
     * Creates a new instance of MercurialRepository
     */
    public SCCSRepository() { }

    /**
     * Use verbose log messages, or just the summary
     * @return true if verbose log messages are used for this repository
     */
    public boolean isVerbose() {
        return verbose;
    }
    
    private String getCommand() {
       return System.getProperty("org.opensolaris.opengrok.history.SCCS", "sccs");
    }
        
    /**
     * Specify if verbose log messages or just the summary should be used
     * @param verbose set to true if verbose messages should be used
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
    
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        try {
            File history = SCCSHistoryParser.getSCCSFile(parent, basename);
            return SCCSget.getRevision(getCommand(),history, rev);
        } catch (FileNotFoundException ex) {
            return null;
        } catch (IOException ex) {
            return null;
        }
    }
    
     /** Pattern used to extract revision from sccs get */
    private final static Pattern AUTHOR_PATTERN =
        Pattern.compile("^([\\d.]+)\\s+(\\S+)");
    
    private void getAuthors(File file) throws IOException {
        //System.out.println("Alloc Authors cache");
        authors_cache = new HashMap<String, String>();

        ArrayList<String> argv = new ArrayList<String>();
        argv.add(getCommand());
        argv.add("prs");
        argv.add("-e");
        argv.add("-d");
        argv.add(":I: :P:");
        argv.add(file.getCanonicalPath());

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(file.getCanonicalFile().getParentFile());
        Process process = null;
        BufferedReader in = null;
        try {
            process = pb.start();
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int lineno = 0;
            while ((line = in.readLine()) != null) {
                ++lineno;
                Matcher matcher = AUTHOR_PATTERN.matcher(line);
                if (matcher.find()) {
                    String rev = matcher.group(1);
                    String auth = matcher.group(2);
                    authors_cache.put(rev, auth);
                } else {
                    System.err.println("Error: did not find annotation in line " + lineno);
                    System.err.println("[" + line + "]");
                }
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }

            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException e) {
                    process.destroy();
                }
            }
        }
    }
    
    public Class<? extends HistoryParser> getHistoryParser() {
        return SCCSHistoryParser.class;
    }

    
    public Class<? extends HistoryParser> getDirectoryHistoryParser() {
        // No directory history with Teamware
        return null;
    }

    /** Pattern used to extract revision from sccs get */
    private final static Pattern ANNOTATION_PATTERN =
        Pattern.compile("^([\\d.]+)\\s+");

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     */
    public Annotation annotate(File file, String revision) throws Exception {
        
        //System.out.println("annotating " + file.getCanonicalPath());
        getAuthors(file);
        
        ArrayList<String> argv = new ArrayList<String>();
        argv.add(getCommand());
        argv.add("get");
        argv.add("-m");
        argv.add("-p");
        if (revision != null) {
            argv.add("-r" + revision);
        }
        argv.add(file.getCanonicalPath());
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(file.getCanonicalFile().getParentFile());
        Process process = null;
        BufferedReader in = null;
        try {
            process = pb.start();
            in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            Annotation a = new Annotation(file.getName());
            String line;
            int lineno = 0;
            while ((line = in.readLine()) != null) {
                ++lineno;
                Matcher matcher = ANNOTATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    String rev = matcher.group(1);
                    String author = authors_cache.get(rev);
                    if (author == null) {
                        author = "unknown";
                    }
                    
                    a.addLine(rev, author, true);
                } else {
                    System.err.println("Error: did not find annotation in line " + lineno);
                    System.err.println("[" + line + "]");
                }
            }
            return a;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException e) {
                    process.destroy();
                }
            }
        }
    }

    public boolean fileHasAnnotation(File file) {
        return true;
    }

    public boolean isCacheable() {
        return false;
    }

    public void update() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean fileHasHistory(File file) {
        String parent = file.getParent();
        String name = file.getName();
        File f = new File(parent + "/SCCS/s." + name);
        if (f.exists()) {
            return true;
        }
        return false;
    }
    
    @Override
    boolean isRepositoryFor(File file) {
        return new File(file, "SCCS").isDirectory();
    }
}

