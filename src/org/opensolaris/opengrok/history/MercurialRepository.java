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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author Trond Norbye
 */
public class MercurialRepository implements ExternalRepository {
    private File directory;
    private String directoryName;
    private String command;
    private boolean verbose;
    private boolean useCache;
    
    
    public MercurialRepository() {
        
    }
    
    /**
     * Creates a new instance of MercurialRepository
     */
    public MercurialRepository(String directory) {
        this.directory = new File(directory);
        directoryName = this.directory.getAbsolutePath();
        command = System.getProperty("org.opensolaris.opengrok.history.Mercurial", "hg");
        useCache = RuntimeEnvironment.getInstance().useHistoryCache();
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public String getCommand() {
        return command;
    }
    
    public boolean isVerbose() {
        return verbose;
    }
    
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
    
    InputStream getHistoryStream(File file) {
        InputStream ret = null;
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
        try {
            Process process =
                    Runtime.getRuntime().exec(argv, null, directory);
            ret = process.getInputStream();
        } catch (Exception ex) {
            System.err.println("An error occured while executing hg log:");
            ex.printStackTrace(System.err);
            ret = null;
        }
        
        return ret;
    }
    
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        MercurialGet ret = null;
        String filename =  (new File(parent, basename)).getAbsolutePath().substring(directoryName.length() + 1);
        
        try {
            String argv[] = { command, "cat", "-r", rev, filename };
            Process process = Runtime.getRuntime().exec(argv, null, directory);
            
            ret = new MercurialGet(process.getInputStream());
        } catch (Exception exp) {
            System.err.print("Failed to get history: " + exp.getClass().toString());
            exp.printStackTrace();
        }
        
        return ret;
    }
    
    public Class<? extends HistoryParser> getHistoryParser() {
        return MercurialHistoryParser.class;
    }
    
    public String getDirectoryName() {
        return directoryName;
    }
    
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
                HistoryCache.writeCacheFile(e.getKey(), e.getValue());
            }
        }
    }
}


