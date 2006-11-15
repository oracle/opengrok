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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)RCSHistoryReader.java 1.2     05/12/01 SMI"
 */

package org.opensolaris.opengrok.history;

import org.apache.commons.jrcs.rcs.*;
import java.util.*;
import java.io.*;
import java.text.*;

/**
 * Virtualise RCS file as a reader, getting a specified version
 */
public class RCSHistoryReader extends HistoryReader {
    
    SimpleDateFormat df;
    private ArrayList<Node> lines;
    private int curline, linepos;
    boolean first = true;
    
    public static void main(String[] args) {
        try{
            long t1 = (new Date()).getTime();
            BufferedReader rr = new BufferedReader(new RCSHistoryReader(args[0]));
            int c;
            System.out.println("-----Reading comments as a reader");
            BufferedOutputStream br = new BufferedOutputStream(System.out);
            while((c = rr.read()) != -1) {
                br.write((char)c);
            }
            br.flush();
            System.out.println("-----Reading comments as lines");
            HistoryReader hr = new RCSHistoryReader(args[0]);
            while(hr.next()) {
                System.out.println(hr.getLine());
            }
            hr.close();
            System.out.println("-----Reading comments structure");
            hr = new RCSHistoryReader(args[0]);
            while(hr.next()) {
                System.out.println("GOT = " + hr.getRevision() + " DATE = "+ hr.getDate() + " AUTHOR= " +
                        hr.getAuthor() + " COMMENT = " + hr.getComment() + " ACTIVE = " + hr.isActive());
            }
            hr.close();
            System.err.println("Took " + ((new Date()).getTime() - t1));
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }
    
    public RCSHistoryReader(String file) throws IOException {
        super(new StringReader(" "));
	//Date startt = new Date();
        df = new SimpleDateFormat("dd-MMM-yyyy");
        Node n = null;
        try {
            Archive archive = new Archive(file);
            Version ver = archive.getRevisionVersion();
            n = archive.findNode(ver);
            n = n.root();
        } catch (org.apache.commons.jrcs.rcs.ParseException e) {
            throw (new IOException());
        }
        lines = new ArrayList<Node>(10);
        traverse(n);
        curline = -1;
        linepos = 0;
	//System.err.println(file + ((new Date()).getTime() - startt.getTime())+ " msec ");
    }
    
    private void traverse(Node n) {
        if (n== null)
            return;
        traverse(n.getChild());
        TreeMap brt = n.getBranches();
        if (brt != null) {
            for (Iterator i = brt.values().iterator(); i.hasNext();) {
                Node b = (Node) i.next();
                traverse(b);
            }
        }
        if(!n.isGhost()) {
            lines.add(n);
        }
    }
    
    public boolean next() {
	//System.err.println(" cur line = " + curline + " lines.size = " + lines.size()); 
        if (curline < lines.size() - 1) {
	    if(curline == -1) curline =0;
            linepos = 0;
            if(first) {
                first = false;
            } else {
                curline++;
            }
            return true;
        }
        return false;
    }
    
    public int read(char[] buffer, int pos, int len) {
        int n = 0;
        if (curline >= lines.size())
            return -1;
        
        while (len > 0 && curline < lines.size()) {
            String line = getLine();
            int linelen = line.length();
            if (( linelen - linepos) <= len) {
                line.getChars(linepos, linelen, buffer, pos);
                int inc = linelen - linepos;
                curline++;
                pos += inc;
                n += inc;
                len -= inc;
                linepos = 0;
            } else {
                line.getChars(linepos, linepos + len, buffer, pos);
                pos += len;
                n += len;
                linepos += len;
                len = 0;
            }
        }
        return n;
    }
    
    public int read() throws IOException {
        throw new IOException("Unsupported read! use a buffer reader to wrap around");
    }
    
    /**
     * @return  get the history line in one String of current log record
     */
    public String getLine() {
	if(curline == -1) curline = 0;
        Node n = lines.get(curline);
        return n.getVersion() + " " + df.format(n.getDate()) + " " + n.getAuthor()
        + " " + n.getLog() + "\n";
    }
    
    /**
     * @return  get the revision string of current log record
     */
    public String getRevision() {
        return lines.get(curline).getVersion().toString();
    }
    
    /**
     * @return  get the date assosiated with current log record
     */
    public Date getDate() {
        return lines.get(curline).getDate();
    }
    
    /**
     * @return  get the author of current log record
     */
    public String getAuthor() {
        return lines.get(curline).getAuthor();
    }
    /**
     * @return  get the comments of current log record
     */
    public String getComment() {
        return lines.get(curline).getLog();
    }
    
    public void close() {
    }

    public boolean isActive() {
        return !lines.get(curline).isGhost();
    }
}
