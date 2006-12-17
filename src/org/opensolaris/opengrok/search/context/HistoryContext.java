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
 * ident      "@(#)HistoryContext.java 1.2     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.context;

import java.io.*;
import java.util.*;
import org.apache.lucene.search.*;
import org.apache.commons.jrcs.rcs.*;
import org.apache.commons.jrcs.diff.*;
import org.opensolaris.opengrok.history.*;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.web.Util;

/**
 * it is supposed to get the matching lines from history log files.
 * since lucene does not easily give the match context.
 */
public class HistoryContext {
    private LineMatcher[] m;
    HistoryLineTokenizer tokens;
    String filename;
    private static Set<String> tokenFields = new HashSet<String>(1);
    static {
        tokenFields.add("hist");
    }
        
    public HistoryContext(Query query) {
        QueryMatchers qm = new QueryMatchers();
        m = qm.getMatchers(query, tokenFields);
        if(m != null) {
            tokens = new HistoryLineTokenizer((Reader)null);
        }
        filename = null;
    }
    public boolean isEmpty() {
        return m == null;
    }
    public boolean getContext(String filename, String path, List<Hit> hits) throws FileNotFoundException, IOException {
        if (m == null) {
            return false;
        }
        File f = new File(filename);
        this.filename = filename;
        return getHistoryContext(HistoryGuru.getInstance().getHistoryReader(f),
                                 path, null, hits);
        
    }
    
    public boolean getContext(String parent, String basename, String path, Writer out) throws FileNotFoundException, IOException {
        if (m == null) {
            return false;
        }
        HistoryReader hr = HistoryGuru.getInstance().getHistoryReader(
                             new File(parent, basename));
        return getHistoryContext(hr, path, out, null);
    }
    
    /**
     * Writes matching History log entries from 'in' to 'out'
     * @param in pass HistoryReader
     * @param out to write matched context
     */
    private boolean getHistoryContext(HistoryReader in, String path, Writer out, List<Hit> hits) {
        if (m == null) {
            return false;
        }
        tokens.setWriter(out);
        tokens.setHitList(hits);
        tokens.setFilename(path);
        
        String line;
        int matchedLines = 0;
        try {
            while(in.next() && matchedLines < 10) {
                char[] content = in.getLine().toCharArray();
                tokens.reInit(content);
                String token;
                int matchState = LineMatcher.NOT_MATCHED;
                while ((token = tokens.next()) != null) {
                    for (int i = 0; i< m.length; i++) {
                        matchState = m[i].match(token);
                        if (matchState == LineMatcher.MATCHED) {
                            tokens.printContext();
                            tokens.dumpRest();
                            matchedLines++;
                            break;
                        } else if (matchState == LineMatcher.WAIT) {
                            tokens.holdOn();
                        } else {
                            tokens.neverMind();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e);
        }
        return matchedLines > 0;
    }
    
    public static void main(String[] args) {
        try{
            Query qry = org.apache.lucene.queryParser.QueryParser.parse(
                    args[0],
                    "hist",
                    new CompatibleAnalyser());
            System.out.println("Query = " + qry.toString());
            HistoryContext ctx = new HistoryContext(qry);
            Date start = new Date();
            Writer out = new BufferedWriter(new OutputStreamWriter(System.out));
            File sfile = new File(args[1]);
            ctx.getContext(sfile.getParent(), sfile.getName(), sfile.getPath(), out);
            long span =  ((new Date()).getTime() - start.getTime());
            System.err.println("took: "+ span + " msec");
            out.flush();
        } catch (Exception e) {
            System.err.println("Error: " + e + "\n Usage HistoryContext 'query' s.file");
            e.printStackTrace();
        }
    }
}
