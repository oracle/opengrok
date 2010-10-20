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
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.search.context;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.apache.lucene.search.Query;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.history.History;
import org.opensolaris.opengrok.history.HistoryEntry;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.search.Hit;

/**
 * it is supposed to get the matching lines from history log files.
 * since lucene does not easily give the match context.
 */
public class HistoryContext {
    private final LineMatcher[] m;
    HistoryLineTokenizer tokens;
    private static final Set<String> tokenFields =
            Collections.singleton("hist");

    public HistoryContext(Query query) {
        QueryMatchers qm = new QueryMatchers();
        m = qm.getMatchers(query, tokenFields);
        if(m != null) {
            tokens = new HistoryLineTokenizer((Reader)null);
        }
    }
    public boolean isEmpty() {
        return m == null;
    }

    public boolean getContext(String filename, String path, List<Hit> hits)
            throws HistoryException
    {
        if (m == null) {
            return false;
        }
        File f = new File(filename);
        return getHistoryContext(HistoryGuru.getInstance().getHistory(f),
                                 path, null, hits);
        
    }
    
    public boolean getContext(
            String parent, String basename, String path, Writer out)
            throws HistoryException
    {
        if (m == null) {
            return false;
        }
        History hist = HistoryGuru.getInstance().getHistory(
                             new File(parent, basename));
        return getHistoryContext(hist, path, out, null);
    }
    
    /**
     * Writes matching History log entries from 'in' to 'out'
     * @param in the history to fetch entries from
     * @param out to write matched context
     */
    private boolean getHistoryContext(
            History in, String path, Writer out, List<Hit> hits) {
        if ((out == null) == (hits == null)) {
            // There should be exactly one destination for the output. If
            // none or both are specified, it's a bug.
            throw new IllegalArgumentException(
                    "Exactly one of out and hits should be non-null");
        }

        if (m == null) {
            return false;
        }

        int matchedLines = 0;
        Iterator<HistoryEntry> it = in.getHistoryEntries().iterator();
        try {
            HistoryEntry he=null;
            HistoryEntry nhe=null;
            String nrev=null;
            while(( it.hasNext()||(nhe!=null) ) && matchedLines < 10) {
                if (nhe==null) { he=it.next(); }
                else { he=nhe; } //nhe is the lookahead revision
                String line = he.getLine();
                String rev = he.getRevision();
                if (it.hasNext()) { nhe=it.next();  } //this prefetch mechanism is here because of the diff link generation
                                    // we currently generate the diff to previous revision
                else {nhe=null;}
                if (nhe==null) { nrev=null; }
                else { nrev=nhe.getRevision(); }
                tokens.reInit(line);
                String token;
                int matchState;
                int start = -1;
                while ((token = tokens.next()) != null) {
                    for (int i = 0; i< m.length; i++) {
                        matchState = m[i].match(token);
                        if (matchState == LineMatcher.MATCHED) {
                            if (start < 0) {
                                start = tokens.getMatchStart();
                            }
                            int end = tokens.getMatchEnd();
                            if (out == null) {
                                addHit(hits, line, path, start, end);
                            } else {
                                //print the diff a href
                                //rev has one and nrev second revision
                                //TODO
                                writeMatch(out, line, start, end, false);
                            }
                            matchedLines++;
                            break;
                        } else if (matchState == LineMatcher.WAIT) {
                            if (start < 0) {
                                start = tokens.getMatchStart();
                            }
                        } else {
                            start = -1;
                        }
                    }
                }
            }
        } catch (Exception e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "Could not get history context for " + path, e);
        }
        return matchedLines > 0;
    }

    /**
     * Add a matching log entry to the list of hits.
     *
     * @param hits the list of hits
     * @param line the line with a match
     * @param path the file in which the match is found
     * @param start position of the start of the match
     * @param end position of the first char after the match
     */
    private void addHit(List<Hit> hits, String line, String path,
                        int start, int end) throws IOException {
        StringBuilder sb = new StringBuilder();
        writeMatch(sb, line, start, end, true);
        hits.add(new Hit(path, sb.toString(), "", false, false));
    }

    /**
     * Write a match to a stream.
     *
     * @param out the receiving stream
     * @param line the matching line
     * @param start start position of the match
     * @param end position of the first char after the match
     * @param flatten should multi-line log entries be flattened to a single
     * line? If {@code true}, replace newline with space.
     */
    private void writeMatch(Appendable out, String line,
                            int start, int end, boolean flatten)
            throws IOException {
        String prefix = line.substring(0, start);
        String match = line.substring(start, end);
        String suffix = line.substring(end);

        printHTML(out, prefix, flatten);
        out.append("<b>");
        printHTML(out, match, flatten);
        out.append("</b>");
        printHTML(out, suffix, flatten);
    }

    /**
     * Output a string as HTML.
     *
     * @param out where to write the HTML
     * @param str the string to print
     * @param flatten should multi-line strings be flattened to a single
     * line? If {@code true}, replace newline with space.
     */
    private void printHTML(Appendable out, String str, boolean flatten)
            throws IOException {
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            switch (ch) {
                case '\n':
                    out.append(flatten ? " " : "<br/>");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '&':
                    out.append("&amp;");
                    break;
                default:
                    out.append(ch);
            }
        }
    }
}
