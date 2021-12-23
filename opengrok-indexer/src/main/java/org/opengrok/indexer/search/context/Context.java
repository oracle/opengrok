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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search.context;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.Scopes;
import org.opengrok.indexer.analysis.Scopes.Scope;
import org.opengrok.indexer.analysis.plain.PlainAnalyzerFactory;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.Hit;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.Util;

/**
 * This is supposed to get the matching lines from sourcefile.
 * since Lucene does not easily give the match context.
 */
public class Context {

    static final int MAXFILEREAD = 1024 * 1024;

    private static final Logger LOGGER = LoggerFactory.getLogger(Context.class);

    private final Query query;
    private final QueryBuilder qbuilder;
    private final LineMatcher[] m;
    private final String queryAsURI;

    /**
     * Map whose keys tell which fields to look for in the source file, and
     * whose values tell if the field is case insensitive (true for
     * insensitivity, false for sensitivity).
     */
    private static final Map<String, Boolean> TOKEN_FIELDS = Map.of(
            QueryBuilder.FULL, Boolean.TRUE,
            QueryBuilder.REFS, Boolean.FALSE,
            QueryBuilder.DEFS, Boolean.FALSE
    );

    /**
     * Initializes a context generator for matchers derived from the specified
     * {@code query} -- which might be {@code null} and result in
     * {@link #isEmpty()} returning {@code true}.
     * @param query the query to generate the result for
     * @param qbuilder required builder used to create {@code query}
     */
    public Context(Query query, QueryBuilder qbuilder) {
        if (qbuilder == null) {
            throw new IllegalArgumentException("qbuilder is null");
        }

        this.query = query;
        this.qbuilder = qbuilder;
        QueryMatchers qm = new QueryMatchers();
        m = qm.getMatchers(query, TOKEN_FIELDS);
        if (m != null) {
            queryAsURI = buildQueryAsURI(qbuilder.getQueries());
        } else {
            queryAsURI = "";
        }
    }

    /**
     * Toggles the alternating value (initially {@code true}).
     */
    public void toggleAlt() {
        alt = !alt;
    }

    public boolean isEmpty() {
        return m == null;
    }

    /**
     * Look for context for this instance's initialized query in a search result
     * {@link Document}, and output according to the parameters.
     * @param env required environment
     * @param searcher required search that produced the document
     * @param docId document ID for producing context
     * @param dest required target to write
     * @param urlPrefix prefix for links
     * @param morePrefix optional link to more... page
     * @param limit a value indicating if the number of matching lines should be
     * limited. N.b. unlike
     * {@link #getContext(java.io.Reader, java.io.Writer, java.lang.String, java.lang.String, java.lang.String,
     * org.opengrok.indexer.analysis.Definitions, boolean, boolean, java.util.List, org.opengrok.indexer.analysis.Scopes)},
     * the {@code limit} argument will not be interpreted w.r.t.
     * {@link RuntimeEnvironment#isQuickContextScan()}.
     * @param tabSize optional positive tab size that must accord with the value
     * used when indexing or else postings may be wrongly shifted until
     * re-indexing
     * @return Did it get any matching context?
     */
    public boolean getContext2(RuntimeEnvironment env, IndexSearcher searcher,
        int docId, Appendable dest, String urlPrefix, String morePrefix,
        boolean limit, int tabSize) {

        if (isEmpty()) {
            return false;
        }

        Document doc;
        try {
            doc = searcher.doc(docId);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "ERROR getting searcher doc(int)", e);
            return false;
        }

        Definitions tags = null;
        try {
            IndexableField tagsField = doc.getField(QueryBuilder.TAGS);
            if (tagsField != null) {
                tags = Definitions.deserialize(tagsField.binaryValue().bytes);
            }
        } catch (ClassNotFoundException | IOException e) {
            LOGGER.log(Level.WARNING, "ERROR Definitions.deserialize(...)", e);
            return false;
        }

        Scopes scopes;
        try {
            IndexableField scopesField = doc.getField(QueryBuilder.SCOPES);
            if (scopesField != null) {
                scopes = Scopes.deserialize(scopesField.binaryValue().bytes);
            } else {
                scopes = new Scopes();
            }
        } catch (ClassNotFoundException | IOException e) {
            LOGGER.log(Level.WARNING, "ERROR Scopes.deserialize(...)", e);
            return false;
        }

        /*
         * UnifiedHighlighter demands an analyzer "even if in some
         * circumstances it isn't used"; here it is not meant to be used.
         */
        PlainAnalyzerFactory fac = PlainAnalyzerFactory.DEFAULT_INSTANCE;
        AbstractAnalyzer anz = fac.getAnalyzer();

        String path = doc.get(QueryBuilder.PATH);
        String pathE = Util.uriEncodePath(path);
        String urlPrefixE = urlPrefix == null ? "" : Util.uriEncodePath(urlPrefix);
        String moreURL = morePrefix == null ? null : Util.uriEncodePath(morePrefix) + pathE + "?" + queryAsURI;

        ContextArgs args = new ContextArgs(env.getContextSurround(), env.getContextLimit());
        /*
         * Lucene adds to the following value in FieldHighlighter, so avoid
         * integer overflow by not using Integer.MAX_VALUE -- Short is good
         * enough.
         */
        int linelimit = limit ? args.getContextLimit() : Short.MAX_VALUE;

        ContextFormatter formatter = new ContextFormatter(args);
        formatter.setUrl(urlPrefixE + pathE);
        formatter.setDefs(tags);
        formatter.setScopes(scopes);
        formatter.setMoreUrl(moreURL);
        formatter.setMoreLimit(linelimit);

        OGKUnifiedHighlighter uhi = new OGKUnifiedHighlighter(env, searcher, anz);
        uhi.setBreakIterator(StrictLineBreakIterator::new);
        uhi.setFormatter(formatter);
        uhi.setTabSize(tabSize);

        try {
            List<String> fieldList = qbuilder.getContextFields();
            String[] fields = fieldList.toArray(new String[0]);

            String res = uhi.highlightFieldsUnion(fields, query, docId,
                linelimit);
            if (res != null) {
                dest.append(res);
                return true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "ERROR highlightFieldsUnion(...)", e);
            // Continue below.
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "ERROR highlightFieldsUnion(...)", e);
            throw e;
        }
        return false;
    }

    /**
     * Build the {@code queryAsURI} string that holds the query in a form
     * that's suitable for sending it as part of a URI.
     *
     * @param subqueries a map containing the query text for each field
     */
    private String buildQueryAsURI(Map<String, String> subqueries) {
        if (subqueries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : subqueries.entrySet()) {
            String field = entry.getKey();
            String queryText = entry.getValue();
            sb.append(field).append("=").append(Util.uriEncode(queryText)).append('&');
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private boolean alt = true;

    public boolean getContext(Reader in, Writer out, String urlPrefix,
        String morePrefix, String path, Definitions tags,
        boolean limit, boolean isDefSearch, List<Hit> hits) {
        return getContext(in, out, urlPrefix, morePrefix, path, tags, limit, isDefSearch, hits, null);
    }
    /**
     * ???.
     * Closes the given <var>in</var> reader on return.
     *
     * @param in File to be matched
     * @param out to write the context
     * @param urlPrefix URL prefix
     * @param morePrefix to link to more... page
     * @param path path of the file
     * @param tags format to highlight defs.
     * @param limit should the number of matching lines be limited?
     * @param isDefSearch is definition search
     * @param hits list of hits
     * @param scopes scopes object
     * @return Did it get any matching context?
     */
    public boolean getContext(Reader in, Writer out, String urlPrefix,
            String morePrefix, String path, Definitions tags,
            boolean limit, boolean isDefSearch, List<Hit> hits, Scopes scopes) {
        if (m == null) {
            IOUtils.close(in);
            return false;
        }
        boolean anything = false;
        TreeMap<Integer, String[]> matchingTags = null;
        String urlPrefixE = (urlPrefix == null) ? "" : Util.uriEncodePath(urlPrefix);
        String pathE = Util.uriEncodePath(path);
        if (tags != null) {
            matchingTags = new TreeMap<>();
            try {
                for (Definitions.Tag tag : tags.getTags()) {
                    for (LineMatcher lineMatcher : m) {
                        if (lineMatcher.match(tag.symbol) == LineMatcher.MATCHED) {
                            String scope = null;
                            String scopeUrl = null;
                            if (scopes != null) {
                                Scope scp = scopes.getScope(tag.line);
                                scope = scp.getName() + "()";
                                scopeUrl = "<a href=\"" + urlPrefixE + pathE + "#" +
                                        scp.getLineFrom() + "\">" + scope + "</a>";
                            }

                            /* desc[0] is matched symbol
                             * desc[1] is line number
                             * desc[2] is type
                             * desc[3] is matching line;
                             * desc[4] is scope
                             */
                            String[] desc = {
                                    tag.symbol,
                                    Integer.toString(tag.line),
                                    tag.type,
                                    tag.text,
                                    scope,
                            };
                            if (in == null) {
                                if (out == null) {
                                    Hit hit = new Hit(path,
                                            Util.htmlize(desc[3]).replace(
                                                    desc[0], "<b>" + desc[0] + "</b>"),
                                            desc[1], false, alt);
                                    hits.add(hit);
                                } else {
                                    out.write("<a class=\"s\" href=\"");
                                    out.write(urlPrefixE);
                                    out.write(pathE);
                                    out.write("#");
                                    out.write(desc[1]);
                                    out.write("\"><span class=\"l\">");
                                    out.write(desc[1]);
                                    out.write("</span> ");
                                    out.write(Util.htmlize(desc[3]).replace(
                                            desc[0], "<b>" + desc[0] + "</b>"));
                                    out.write("</a> ");

                                    if (desc[4] != null) {
                                        out.write("<span class=\"scope\"><a href\"");
                                        out.write(scopeUrl);
                                        out.write("\">in ");
                                        out.write(desc[4]);
                                        out.write("</a></span> ");
                                    }
                                    out.write("<i>");
                                    out.write(desc[2]);
                                    out.write("</i><br/>");
                                }
                                anything = true;
                            } else {
                                matchingTags.put(tag.line, desc);
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                if (hits != null) {
                    // @todo verify why we ignore all exceptions?
                    LOGGER.log(Level.WARNING, "Could not get context for " + path, e);
                }
            }
        }

        // Just to get the matching tag send a null in
        if (in == null) {
            return anything;
        }

        PlainLineTokenizer tokens = new PlainLineTokenizer(null);
        boolean truncated = false;
        boolean lim = limit;
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (!env.isQuickContextScan()) {
            lim = false;
        }

        if (lim) {
            char[] buffer = new char[MAXFILEREAD];
            int charsRead;
            try {
                charsRead = in.read(buffer);
                if (charsRead == MAXFILEREAD) {
                    // we probably only read parts of the file, so set the
                    // truncated flag to enable the [all...] link that
                    // requests all matches
                    truncated = true;
                    // truncate to last line read (don't look more than 100
                    // characters back)
                    for (int i = charsRead - 1; i > charsRead - 100; i--) {
                        if (buffer[i] == '\n') {
                            charsRead = i;
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "An error occurred while reading data", e);
                return anything;
            }
            if (charsRead == 0) {
                return anything;
            }

            tokens.reInit(buffer, charsRead, out, urlPrefixE + pathE + "#", matchingTags, scopes);
        } else {
            tokens.reInit(in, out, urlPrefixE + pathE + "#", matchingTags, scopes);
        }

        if (hits != null) {
            tokens.setAlt(alt);
            tokens.setHitList(hits);
            tokens.setFilename(path);
        }

        int limit_max_lines = env.getContextLimit();
        try {
            String token;
            int matchState;
            int matchedLines = 0;
            while ((token = tokens.yylex()) != null && (!lim ||
                    matchedLines < limit_max_lines)) {
                for (LineMatcher lineMatcher : m) {
                    matchState = lineMatcher.match(token);
                    if (matchState == LineMatcher.MATCHED) {
                        if (!isDefSearch) {
                            tokens.printContext();
                        } else if (tokens.tags.containsKey(tokens.markedLine)) {
                            tokens.printContext();
                        }
                        matchedLines++;
                        break;
                    } else if (matchState == LineMatcher.WAIT) {
                        tokens.holdOn();
                    } else {
                        tokens.neverMind();
                    }
                }
            }
            anything = matchedLines > 0;
            tokens.dumpRest();
            if (lim && (truncated || matchedLines == limit_max_lines) && out != null) {
                out.write("<a href=\"" + Util.uriEncodePath(morePrefix) + pathE + "?" + queryAsURI + "\">[all...]</a>");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not get context for " + path, e);
        } finally {
            IOUtils.close(in);

            if (out != null) {
                try {
                    out.flush();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to flush stream: ", e);
                }
            }
        }
        return anything;
    }
}
