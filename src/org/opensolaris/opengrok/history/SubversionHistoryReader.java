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
 * ident	"%Z%%M% %I%     %E% SMI"
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import org.tigris.subversion.javahl.SVNClient;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.LogMessage;

/**
 * Read out version history for a given file.
 *
 * @author Trond Norbye
 */
public class SubversionHistoryReader extends HistoryReader {
    private SimpleDateFormat df;
    private String parent;
    private String name;
    private int entry;
    private StringBuilder stringbuilder;
    private StringReader input;
    private boolean initialized;
    private boolean records;
    private LogMessage messages[];
    
    public SubversionHistoryReader(String parent, String name) throws Exception {
        super(new StringReader(""));
        this.parent = parent;
        this.name = name;
        entry = -1;
        df = new SimpleDateFormat("dd-MMM-yyyy");
        input = null;
        initialized = false;
// long start = System.currentTimeMillis();
        try {
            SVNClient client = new SVNClient();
            messages = client.logMessages(parent + "/" + name, Revision.START, Revision.HEAD);
        } catch (Exception exp) {
            System.err.println(exp.getMessage());
            throw exp;
        }
        
// long stop = System.currentTimeMillis();
        // System.out.println(name + " " + (stop - start) + "ms");
    }
    
    private void initialize(boolean vector) throws java.io.IOException {
        records = vector;
        if (!vector) {
            stringbuilder = new StringBuilder();
            for (int ii = 0; ii < messages.length; ++ii) {
                stringbuilder.append(messages[ii].getRevision());
                stringbuilder.append(" ");
                stringbuilder.append(df.format(messages[ii].getDate()));
                stringbuilder.append(" ");
                stringbuilder.append(messages[ii].getAuthor());
                stringbuilder.append(" ");
                stringbuilder.append(messages[ii].getMessage());
                stringbuilder.append("\n");
            }
            input = new StringReader(stringbuilder.toString());
        } else {
            entry = 0;
        }
        initialized = true;
    }
    
    public boolean next() throws java.io.IOException {
        boolean ret = true;
        
        if (!initialized) {
            initialize(true);
        } else {
            if ((entry + 1) < messages.length) {
                ++entry;
            } else {
                ret = false;
            }
        }
        
        return ret;
    }
    
    public int read(char[] buffer, int pos, int len) throws IOException {
        if (!initialized) {
            initialize(false);
        }
        
        int ret;
        
        if (input == null) {
            ret = -1;
        } else {
            ret = input.read(buffer, pos, len);
        }
        
        return ret;
    }
    
    public int read() throws IOException {
        throw new IOException("Unsupported read! use a buffer reader to wrap around");
    }
    
    public boolean isActive() {
        return true;
    }
    
    public String getRevision() {
        return messages[entry].getRevision().toString();
    }
    
    public String getLine() {
        return messages[entry].getRevision() + " " + df.format(messages[entry].getDate()) + " " + messages[entry].getAuthor()
        + " " + messages[entry].getMessage() + "\n";
    }
    
    public java.util.Date getDate() {
        return messages[entry].getDate();
    }
    
    public String getComment() {
        return messages[entry].getMessage();
    }
    
    public String getAuthor() {
        return messages[entry].getAuthor();
    }
    
    public void close() throws java.io.IOException {
        if (input != null) {
            input.close();
        }
    }
    
    /**
     * Test function to test the SubversionHistoryReader
     * @param args argument vector (containing the files to test).
     */
    public static void main(String[] args) throws Exception  {
        for (int ii = 0; ii < args.length; ++ii) {
            File file = new File(args[0]);
            System.out.println("Got = " + file + " - " + file.getParent() + " === " + file.getName());
            HistoryReader structured = new SubversionHistoryReader(file.getParent(), file.getName());
            structured.testHistoryReader(STRUCTURED);
            structured = new SubversionHistoryReader(file.getParent(), file.getName());
            structured.testHistoryReader(LINE);
            structured = new SubversionHistoryReader(file.getParent(), file.getName());
            structured.testHistoryReader(READER);
        }
    }
}
