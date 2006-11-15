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
 * ident	"@(#)SCCSHistoryReader.java 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.history;

import java.io.*;
import java.util.*;
import java.text.*;

/**
 * Reads and filters out junk from a SCCS history file
 * see sccsfile(4) for details of the file format
 * Wrote it since invoking sccs prs for each file was
 * taking a lot of time. Time to index history has reduced 4 to 1!
 */
public class SCCSHistoryReader extends HistoryReader {
    boolean pass;
    boolean passRecord;
    boolean active;
    int field;
    boolean sep;
    StringBuilder record = new StringBuilder(128);
    private String revision;
    private String author;
    private Date rdate;
    private String comment;
    DateFormat sccsDateFormat;
    
    public SCCSHistoryReader(Reader in) {
        super(in);
        pass = sep = false;
        passRecord = true;
        active = true;
        field = 0;
        sccsDateFormat =  new SimpleDateFormat("yy/MM/dd");
    }
    
    /**
     * Read a single line of delta record
     *
     * @throws java.io.IOException
     * @return a String representing a single log delta entry
     *       rev date time author comments(s)
     */
    public boolean next() throws java.io.IOException {
        sep = true;
        record.setLength(0);
        int c;
        while ((c = read()) > 01) {
            record.append((char)c);
        }
        // to flag that revision needs to be re populated if you really need it
        revision = null;
        if (record.length() > 2) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * @return  get the history line in one String of current log record
     */
    public String getLine() {
        return record.toString();
    }
    
    private void initFields() {
        if(revision == null) {
            String[] f = record.toString().split(" ", 6);
            if (f.length > 5) {
                revision = f[1];
                try {
                    rdate = sccsDateFormat.parse(f[2] + " " + f[3]);
                } catch (ParseException e) {
                    rdate = null;
                }
                author = f[4];
                comment = f[5];
            } else {
                rdate = null;
                author = null;
                comment = null;
            }
        }
    }
    
    /**
     * @return  get the revision string of current log record
     */
    public String getRevision() {
        initFields();
        return revision;
    }
    
    /**
     * @return  get the date assosiated with current log record
     */
    public Date getDate() {
        initFields();
        return rdate;
    }
    
    /**
     * @return  get the author of current log record
     */
    public String getAuthor() {
        initFields();
        return author;
    }
    /**
     * @return  get the comments of current log record
     */
    public String getComment() {
        initFields();
        return comment;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public final int read(char[] buf, int start, int len) throws java.io.IOException {
        int n=0;
        int c;
        while((c = this.read()) > -1 && n <= len && start < buf.length) {
            buf[start++] = (char) c;
            n++;
        }
        if (c == -1 && n == 0 && len != 0)
            return -1;
        return n;
    }
    
    public final int read() throws java.io.IOException {
        int c, d, dt;
        while((c = in.read()) != -1) {
            switch (c) {
                case 01:
                    d = in.read();
                    switch (d) {
                        case 'c':
                        case 't':
                        case 'u':
                            d = in.read();
                            if(d != ' ') {
                                return (d);
                            }
                            pass = true;
                            break;
                        case 'd':
                            d = in.read();
                            if (d != ' ') {
                                return (d);
                            } else {
                                dt = in.read();
                                if (dt == 'R') {
                                    active = false;
                                } else {
                                    active = true;
                                }
                                passRecord = true;
                                field = 1;
                            }
                            break;
                        case -1:
                        case 'I':	//the file contents start
                        case 'D':
                        case 'E':
                        case 'T':
                            return(-1);
                        case 'e':
                            pass = false;
                            if (sep && passRecord) {
                                return 01;
                            } else {
                                passRecord = true;
                            }
                            break;
                        default:
                            pass = false;
                    }
                    break;
                case ' ':
                    if (passRecord) {
                        if (field > 0) {
                            field ++;
                            pass = true;
                        }
                        if(field > 5) {
                            field = 0;
                            pass = false;
                            return(c);
                        }
                    }
                default:
                    if (pass && passRecord) {
                        return(c);
                    }
            }
        }
        return(-1);
    }
    
    public void close() throws IOException {
        in.close();
    }
    public static void main(String[] args) {
        try {
            System.out.println("--------Reading as a structred");
            HistoryReader r = new SCCSHistoryReader(new BufferedReader(new FileReader(args[0])));
            while(r.next()) {
                System.out.println("rev=" + r.getRevision());
                System.out.println("date=" + r.getDate());
                System.out.println("author=" + r.getAuthor());
                System.out.println("comment=" + r.getComment());
                System.out.println("active=" + r.isActive());
            }
            System.out.println("--------Reading as a reader");
            BufferedReader hr = new BufferedReader(new SCCSHistoryReader(new BufferedReader(new FileReader(args[0]))));
            int c;
            while((c = hr.read()) != -1) {
                System.out.write((char) c);
            }
            System.out.println("--------Reading line by line");
            HistoryReader hr2 = new SCCSHistoryReader(new BufferedReader(new FileReader(args[0])));
            while(hr2.next()) {
                System.out.println("line=" + hr2.getLine());
            }
        } catch (Exception e) {
            System.err.println(e + "Usage SCCSreader SCCS/s.file\n");
        }
    }
}
