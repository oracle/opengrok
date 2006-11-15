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
 * ident	"@(#)HistoryReader.java 1.2     06/02/22 SMI"
 */

package org.opensolaris.opengrok.history;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;

/**
 * Abstract class for reading history of a source file. The HistoryReader have
 * tree mutually exclusive usages:
 * <ol>
 *   <li>where you read it as if from a Reader (used by lucene)</li>
 *   <li>you read each entry as one string ( one line = rev + date + author +
 *       comment) used for showing matching context in search results. '\n'
 *       doesn't matter.</li>
 *   <li>you read it in a structured way. (used by history.jsp)</li>
 * </ol>
 * Please note that it is the clients responsibility that if one access pattern
 * is used, it should not switch access method.
 */
abstract public class HistoryReader extends FilterReader {
    public HistoryReader(Reader in) {
        super(in);
    }
    
    /**
     * Read a single line of delta record and sets
     *
     * @return true if more log records exist
     * Eg.
     * do {
     *    r.getRevision();
     * } while(r.next())
     *
     */
    abstract public boolean next() throws IOException;
    
    /**
     * @return  get the history line in one String of current log record
     */
    abstract public String getLine();
    
    /**
     * @return  get the revision string of current log record
     */
    abstract public String getRevision();
    
    /**
     * @return  get the date assosiated with current log record
     */
    abstract public Date getDate();
    
    /**
     * @return  get the author of current log record
     */
    abstract public String getAuthor();
    
    /**
     * @return  get the comments of current log record
     */
    abstract public String getComment();
    
    /**
     * @return  Does current log record is actually point to a revision
     */
    abstract public boolean isActive();
    
    abstract public void close() throws IOException;
    
    public ArrayList<String> getFiles() {
        return null;
    }

    protected static final int STRUCTURED = 0;
    protected static final int LINE = 1;
    protected static final int READER = 2;
    
    /**
     * Each implemetation of the HistoryReader should provide a main function
     * to allow testing the class. The implementation of the main-class should
     * create tree instances of itself and call this method.
     * @param mode The mode (STRUCTURED, READER, LINE) to test the class in
     */
    protected void testHistoryReader(int mode) {
        try {
            switch (mode) {
                case STRUCTURED:
                    System.out.println("--------Reading as a structred");
                    while (next()) {
                        System.out.println("rev=" + getRevision());
                        System.out.println("date=" + getDate());
                        System.out.println("author=" + getAuthor());
                        System.out.println("comment=" + getComment());
                        System.out.println("active=" + isActive());
                    }
                    break;
                case READER:
                    System.out.println("--------Reading as a reader");
                    int c;
                    while((c = read()) != -1) {
                        System.out.write((char) c);
                    }
                    break;
                case LINE:
                    System.out.println("--------Reading line by line");
                    while(next()) {
                        System.out.println("line=" + getLine());
                    }   
                    break;
                default:
                    System.err.println("Unknow test.");
            }
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace(System.err);
        }
    }
}
