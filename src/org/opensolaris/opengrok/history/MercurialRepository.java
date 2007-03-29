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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Access to a Mercurial repository.
 * 
 */
public class MercurialRepository implements ExternalRepository {
    private File directory;
    private String directoryName;
    private String command;
    private boolean verbose;
    
    /**
     * Creates a new instance of MercurialRepository
     */
    public MercurialRepository() { }
    
    /**
     * Creates a new instance of MercurialRepository
     * @param directory The directory containing the .hg-subdirectory
     */
    public MercurialRepository(String directory) {
        this.directory = new File(directory);
        directoryName = this.directory.getAbsolutePath();
        command = System.getProperty("org.opensolaris.opengrok.history.Mercurial", "hg");
    }
    
    /**
     * Set the name of the Mercurial command to use
     * @param command the name of the command (hg)
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Get the name of the Mercurial command that should be used
     * @return the name of the hg command in use
     */
    public String getCommand() {
        return command;
    }
    
    /**
     * Use verbose log messages, or just the summary
     * @return true if verbose log messages are used for this repository
     */
    public boolean isVerbose() {
        return verbose;
    }
        
    /**
     * Specify if verbose log messages or just the summary should be used
     * @param verbose set to true if verbose messages should be used
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
    
    Process getHistoryLogProcess(File file) throws IOException {
        String abs = file.getAbsolutePath();
        String filename = "";
        if (abs.length() > directoryName.length()) {
            filename = abs.substring(directoryName.length() + 1);
        }
        
        String argv[];
        if (verbose || file.isDirectory()) {
            argv = new String[] { command, "log", "-v", filename };
        } else {
            argv = new String[] { command, "log", filename };
        }
        
        return Runtime.getRuntime().exec(argv, null, directory);        
    }    
    
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        InputStream ret = null;

        String filename =  (new File(parent, basename)).getAbsolutePath().substring(directoryName.length() + 1);
        Process process = null;
        try {
            String argv[] = { command, "cat", "-r", rev, filename };
            process = Runtime.getRuntime().exec(argv, null, directory);
            
            StringBuilder sb = new StringBuilder();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String s;
            while ((s = in.readLine()) != null) {
                sb.append(s);
                sb.append("\n");
            }
            
            ret = new BufferedInputStream(new ByteArrayInputStream(sb.toString().getBytes()));
        } catch (Exception exp) {
            System.err.print("Failed to get history: " + exp.getClass().toString());
            exp.printStackTrace();
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
    
    public Class<? extends HistoryParser> getHistoryParser() {
        return MercurialHistoryParser.class;
    }
    
    /**
     * Get the name of the root directory for this repository
     * @return the name of the directory containing the .hg subdirectory
     */
    public String getDirectoryName() {
        return directoryName;
    }
    
    /**
     * Specify the name of the root directory for this repository
     * @param directoryName the new name of the directory containing the .hg 
     *        subdirectory
     */
    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
        this.directory = new File(this.directoryName);
    }
    
    public void createCache() throws IOException, ParseException {
        MercurialHistoryParser p = new MercurialHistoryParser();
        System.out.println("Update Mercurial History Cache for " + directory);
        System.out.flush();
        History history = p.parse(directory, this);
        if (history != null && history.getHistoryEntries() != null) {
            HashMap<String, ArrayList<HistoryEntry>> map = new HashMap<String, ArrayList<HistoryEntry>>();
            for (HistoryEntry e : history.getHistoryEntries()) {
                for (String s : e.getFiles()) {
                    ArrayList<HistoryEntry> list = map.get(s);
                    if (list == null) {
                        list = new ArrayList<HistoryEntry>();
                        list.add(e);
                        map.put(s, list);
                    } else {
                        list.add(e);
                    }
                }
            }
            
            for (Map.Entry<String, ArrayList<HistoryEntry>> e : map.entrySet()) {
                for (HistoryEntry ent : e.getValue()) {
                    ent.strip();
                }
                
                History hist = new History();
                hist.setHistoryEntries(e.getValue());                
                HistoryCache.writeCacheFile(e.getKey(), hist);
            }
        }
    }
}


