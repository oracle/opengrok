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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search.context;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.search.Query;
import org.opengrok.indexer.history.History;
import org.opengrok.indexer.history.HistoryEntry;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.Hit;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.indexer.web.Util;

/**
 * it is supposed to get the matching lines from history log files.
 * since Lucene does not easily give the match context.
 */
public class HistoryContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryContext.class);

    private final LineMatcher[] m;
    HistoryLineTokenizer tokens;

    /**
     * Map whose keys tell which fields to look for in the history, and
     * whose values tell if the field is case insensitive (true for
     * insensitivity, false for sensitivity).
     */
    private static final Map<String, Boolean> tokenFields =
            Collections.singletonMap(QueryBuilder.HIST, Boolean.TRUE);

    public HistoryContext(Query query) {
        QueryMatchers qm = new QueryMatchers();
        m = qm.getMatchers(query, tokenFields);
        if (m != null) {
            tokens = new HistoryLineTokenizer((Reader) null);
        }
    }
    public boolean isEmpty() {
        return m == null;
    }

    public boolean getContext(String filename, String path, List<Hit> hits) throws HistoryException {
        if (m == null) {
            return false;
        }
        File f = new File(filename);
        return getHistoryContext(HistoryGuru.getInstance().getHistory(f), path, null, hits, null);

    }

    public boolean getContext(String parent, String basename, String path, Writer out, String context)
            throws HistoryException {
        return getContext(new File(parent, basename), path, out, context);
    }

    /**
     * Obtain the history for the source file <var>src</var> and write out
     * matching History log entries.
     *
     * @param src       the source file represented by <var>path</var>
     *                  (SOURCE_ROOT + path)
     * @param path      the path of the file (rooted at SOURCE_ROOT)
     * @param out       write destination
     * @param context   the servlet context path of the application (the path
     *  prefix for URLs)
     * @return {@code true} if at least one line has been written out.
     * @throws HistoryException history exception
     */
    public boolean getContext(File src, String path, Writer out, String context) throws HistoryException {
        if (m == null) {
            return false;
        }
        History hist = HistoryGuru.getInstance().getHistory(src);
        if (hist == null) {
            LOGGER.log(Level.INFO, "Null history got for {0}", src);
            return false;
        }
        return getHistoryContext(hist, path, out, null, context);
    }

    /**
     * Writes matching History log entries from 'in' to 'out' or to 'hits'.
     * @param in the history to fetch entries from
     * @param out to write matched context
     * @param path path to the file
     * @param hits list of hits
     * @param wcontext web context - beginning of url
     */
    private boolean getHistoryContext(
            History in, String path, Writer out, List<Hit> hits, String wcontext) {
        if (in == null) {
            throw new IllegalArgumentException("`in' is null");
        }
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
            HistoryEntry he;
            HistoryEntry nhe = null;
            String nrev;
            while ((it.hasNext() || (nhe != null)) && matchedLines < 10) {
                if (nhe == null) {
                    he = it.next();
                } else {
                    he = nhe;  //nhe is the lookahead revision
                }
                String line = he.getLine();
                String rev = he.getRevision();
                if (it.hasNext()) {
                    nhe = it.next();
                } else {
                    // this prefetch mechanism is here because of the diff link generation
                    // we currently generate the diff to previous revision
                    nhe = null;
                }
                if (nhe == null) {
                    nrev = null;
                } else {
                    nrev = nhe.getRevision();
                }
                tokens.reInit(line);
                String token;
                int matchState;
                long start = -1;
                while ((token = tokens.next()) != null) {
                    for (LineMatcher lineMatcher : m) {
                        matchState = lineMatcher.match(token);
                        if (matchState == LineMatcher.MATCHED) {
                            if (start < 0) {
                                start = tokens.getMatchStart();
                            }
                            long end = tokens.getMatchEnd();
                            if (start > Integer.MAX_VALUE || end > Integer.MAX_VALUE) {
                                LOGGER.log(Level.INFO, "Unexpected out of bounds for {0}", path);
                            } else if (out == null) {
                                StringBuilder sb = new StringBuilder();
                                writeMatch(sb, line, (int) start, (int) end,
                                        true, path, wcontext, nrev, rev);
                                hits.add(new Hit(path, sb.toString(), "", false, false));
                            } else {
                                writeMatch(out, line, (int) start, (int) end,
                                        false, path, wcontext, nrev, rev);
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
            LOGGER.log(Level.WARNING, "Could not get history context for " + path, e);
        }
        return matchedLines > 0;
    }

    /**
     * Write a match to a stream.
     *
     * @param out the receiving stream
     * @param line the matching line
     * @param start start position of the match
     * @param end position of the first char after the match
     * @param flatten should multi-line log entries be flattened to a single
     * @param path path to the file
     * @param wcontext web context (begin of URL)
     * @param nrev old revision
     * @param rev current revision
     * line? If {@code true}, replace newline with space.
     * @throws IOException IO exception
     */
    protected static void writeMatch(Appendable out, String line,
                            int start, int end, boolean flatten, String path,
                            String wcontext, String nrev, String rev)
            throws IOException {

        String prefix = line.substring(0, start);
        String match = line.substring(start, end);
        String suffix = line.substring(end);

        if (wcontext != null && nrev != null && !wcontext.isEmpty()) {
            out.append("<a href=\"");
            printHTML(out, wcontext + Prefix.DIFF_P +
                    Util.URIEncodePath(path) +
                    "?" + QueryParameters.REVISION_2_PARAM_EQ + Util.URIEncodePath(path) + "@" +
                    rev + "&" + QueryParameters.REVISION_1_PARAM_EQ + Util.URIEncodePath(path) +
                    "@" + nrev + "\" title=\"diff to previous version\"", flatten);
            out.append(">diff</a> ");
        }

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
    private static void printHTML(Appendable out, String str, boolean flatten)
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
