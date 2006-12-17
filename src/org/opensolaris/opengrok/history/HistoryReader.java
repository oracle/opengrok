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
import java.util.List;
import java.util.Iterator;

/**
 * Class for reading history entries. The HistoryReader have
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
public class HistoryReader extends Reader {

    private List<HistoryEntry> entries;
    private Iterator<HistoryEntry> iterator;
    private HistoryEntry current;
    private Reader input;

    HistoryReader() {
    }

    HistoryReader(List<HistoryEntry> entries) {
        this.entries = entries;
        iterator = entries.iterator();
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
    public boolean next() throws IOException {
        if (iterator.hasNext()) {
            current = iterator.next();
            return true;
        }
        return false;
    }
    
    /**
     * @return  get the history line in one String of current log record
     */
    public String getLine() {
        return current.getLine();
    }
    
    /**
     * @return  get the revision string of current log record
     */
    public String getRevision() {
        return current.getRevision();
    }
    
    /**
     * @return  get the date assosiated with current log record
     */
    public Date getDate() {
        return current.getDate();
    }
    
    /**
     * @return  get the author of current log record
     */
    public String getAuthor() {
        return current.getAuthor();
    }
    
    /**
     * @return  get the comments of current log record
     */
    public String getComment() {
        return current.getMessage();
    }
    
    /**
     * @return  Does current log record is actually point to a revision
     */
    public boolean isActive() {
        return current.isActive();
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        if (input == null) {
            input = createInternalReader();
        }
        return input.read(cbuf, off, len);
    }
    
    public void close() throws IOException {
        if (input != null) {
            input.close();
        }
    }

    public ArrayList<String> getFiles() {
        return null;
    }

    private Reader createInternalReader() {
        StringBuilder str = new StringBuilder();
        for (HistoryEntry entry : entries) {
            str.append(entry.getLine());
        }
        return new StringReader(str.toString());
    }
    
}
