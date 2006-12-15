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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Parse a stream of mercurial log comments.
 *
 * @author Trond Norbye
 */
public class MercurialHistoryReader extends HistoryReader {
    private SimpleDateFormat df;
    private StringReader input;
    private List<HistoryEntry> history;
    private Iterator<HistoryEntry> iterator;
    private HistoryEntry entry;
    
    public MercurialHistoryReader(InputStream is) throws Exception {
        super(new StringReader(""));
        df = new SimpleDateFormat("EEE MMM dd hh:mm:ss yyyy ZZZZ");
        history = new ArrayList<HistoryEntry>();
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        
        try {
            String s;
            boolean description = false;
            while ((s = in.readLine()) != null) {
                if (s.startsWith("changeset:")) {
                    if (entry != null) {
                        history.add(entry);
                    }
                    entry = new HistoryEntry();
                    String rev = s.substring("changeset:".length()).trim();
                    if (rev.indexOf(':') != -1) {
                        rev = rev.substring(0, rev.indexOf(':'));
                    }
                    entry.setRevision(rev);
                    description = false;
                } else if (s.startsWith("user:") && entry != null) {
                    entry.setAuthor(s.substring("user:".length()).trim());
                    description = false;
                } else if (s.startsWith("date:") && entry != null) {
                    entry.setDate(s.substring("date:".length()).trim());
                    description = false;
                } else if (s.startsWith("files:") && entry != null) {
                    description = false;
                    // ignore
                } else if (s.startsWith("summary:") && entry != null) {
                    entry.setMessage(s.substring("summary:".length()).trim());
                    description = false;
                } else if (s.startsWith("description:") && entry != null) {
                    description = true;
                } else if (description && entry != null) {
                    entry.appendMessage(s);
                }
            }
            
            if (entry != null) {
                history.add(entry);
            }
        } catch (Exception exp) {
            System.err.print("Failed to get history: " + exp.getClass().toString());
            System.err.println(exp.getMessage());
            throw exp;
        }
    }
    
    protected void writeCache(File cache) {
        PrintWriter wrt = null;
        try {
            wrt = new PrintWriter(new FileWriter(cache));
            
            for (HistoryEntry ent : history) {
                wrt.print("changeset: ");
                wrt.print(ent.getRevision());
                wrt.println(":foo");
                
                wrt.print("user: ");
                wrt.println(ent.getAuthor());
                
                wrt.print("date: ");
                wrt.println(ent.getDate());
                
                wrt.println("description:");
                wrt.println(ent.getMessage().trim());
                
                wrt.println();
            }
            wrt.flush();
            wrt.close();
        } catch (IOException ex) {
            try { wrt.close(); } catch (Exception e) {}
            cache.delete();
        }
    }
    
    public boolean next() throws java.io.IOException {
        boolean ret = true;
        
        if (iterator == null) {
            iterator = history.iterator();
        }
        
        if ((ret = iterator.hasNext())) {
            entry = iterator.next();
        }
        
        return ret;
    }
    
    public int read(char[] buffer, int pos, int len) throws IOException {
        if (input == null) {
            StringBuilder document = new StringBuilder();
            for (HistoryEntry ent : history) {
                document.append(ent.getLine());
                document.append("\n");
            }
            
            input = new StringReader(document.toString());
        }
        
        return input.read(buffer, pos, len);
    }
    
    public int read() throws IOException {
        throw new IOException("Unsupported read! use a buffer reader to wrap around");
    }
    
    public boolean isActive() {
        return true;
    }
    
    public String getRevision() {
        return entry.getRevision();
    }
    
    public String getLine() {
        return getRevision() + " " + getDate() + " " + getAuthor() + " " + getComment() + "\n";
    }
    
    
    public java.util.Date getDate() {
        java.util.Date ret = new java.util.Date();
        try {
            ret = df.parse(entry.getDate());
        } catch (ParseException ex) {
            ex.printStackTrace();
            ret = new java.util.Date();
        }
        
        return ret;
    }
    
    public String getComment() {
        return entry.getMessage();
    }
    
    public String getAuthor() {
        return entry.getAuthor();
    }
    
    public void close() throws java.io.IOException {
        if (input != null) {
            try {
                input.close();
            } catch (Exception e) {
                ;
            }
        }
    }
}
