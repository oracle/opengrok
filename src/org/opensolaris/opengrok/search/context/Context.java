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
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2011 Jens Elkner.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

/**
 * This is supposed to get the matching lines from sourcefile.
 * since lucene does not easily give the match context.
 */
package org.opensolaris.opengrok.search.context;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.Scopes;
import org.opensolaris.opengrok.analysis.Scopes.Scope;
import org.opensolaris.opengrok.analysis.TagDesc;
import org.opensolaris.opengrok.analysis.plain.PlainAnalyzerFactory;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.util.IOUtils;
import org.opensolaris.opengrok.web.Util;

public class Context {

    private static final Logger LOGGER = LoggerFactory.getLogger(Context.class);

    private final Query query;
    private final QueryBuilder qbuilder;
    private final LineMatcher[] m;
    static final int MAXFILEREAD = 1024 * 1024;
    private char[] buffer;
    PlainLineTokenizer tokens;
    String queryAsURI;
    private boolean alt = true;

    /**
     * Map whose keys tell which fields to look for in the source file, and
     * whose values tell if the field is case insensitive (true for
     * insensitivity, false for sensitivity).
     */
    private static final Map<String, Boolean> TOKEN_FIELDS =
            new HashMap<String, Boolean>();
    static {
        TOKEN_FIELDS.put(QueryBuilder.FULL, Boolean.TRUE);
        TOKEN_FIELDS.put(QueryBuilder.REFS, Boolean.FALSE);
        TOKEN_FIELDS.put(QueryBuilder.DEFS, Boolean.FALSE);
    }

    /**
     * Initializes a context generator for matchers derived from the specified
     * {@code query} -- which might be none and result in {@link #isEmpty()}
     * equal to true.
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
            buildQueryAsURI(qbuilder.getQueries());
            //System.err.println("Found Matchers = "+ m.length + " for " + query);
            buffer = new char[MAXFILEREAD];
            tokens = new PlainLineTokenizer((Reader) null);
        }
    }

    /**
     * Toggles the alternating value (initially {@code true}).
     */
    public void toggleAlt() {
        alt = !alt;
    }

    /**
     * Gets a value indicating if no matchers were derived from the initialized
     * {@link Query}.
     * @return {@code true} if no matchers were derived
     */
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
     * {@link #getContext(java.io.Reader, java.io.Writer, java.lang.String, java.lang.String, java.lang.String, org.opensolaris.opengrok.analysis.Definitions, boolean, boolean, java.util.List, org.opensolaris.opengrok.analysis.Scopes)},
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
        } catch (ClassNotFoundException|IOException e) {
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
        } catch (ClassNotFoundException|IOException e) {
            LOGGER.log(Level.WARNING, "ERROR Scopes.deserialize(...)", e);
            return false;
        }

        /*
         * UnifiedHighlighter demands an analyzer "even if in some
         * circumstances it isn't used"; here it is not meant to be used.
         */
        PlainAnalyzerFactory fac = PlainAnalyzerFactory.DEFAULT_INSTANCE;
        FileAnalyzer anz = fac.getAnalyzer();
        anz.setAllNonWhitespace(env.isAllNonWhitespace());

        String path = doc.get(QueryBuilder.PATH);
        String pathE = Util.URIEncodePath(path);
        String urlPrefixE = urlPrefix == null ? "" : Util.URIEncodePath(
            urlPrefix);
        String moreURL = morePrefix == null ? null : Util.URIEncodePath(
            morePrefix) + pathE + "?" + queryAsURI;

        ContextArgs args = new ContextArgs(env.getContextSurround(),
            env.getContextLimit());
        /**
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

        OGKUnifiedHighlighter uhi = new OGKUnifiedHighlighter(env,
            searcher, anz);
        uhi.setBreakIterator(() -> new StrictLineBreakIterator());
        uhi.setFormatter(formatter);
        uhi.setTabSize(tabSize);

        try {
            List<String> fieldList = qbuilder.getContextFields();
            String[] fields = fieldList.toArray(new String[fieldList.size()]);

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
    private void buildQueryAsURI(Map<String, String> subqueries) {
        if (subqueries.isEmpty()) {
            queryAsURI = "";
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : subqueries.entrySet()) {
            String field = entry.getKey();
            String queryText = entry.getValue();
            if (QueryBuilder.FULL.equals(field)) {
                field = "q"; // bah - search query params should be consistent!
            }
            sb.append(field).append("=").append(Util.URIEncode(queryText))
                .append('&');
        }
        sb.setLength(sb.length()-1);
        queryAsURI = sb.toString();
    }

    /**
     * Calls
     * {@link #getContext(java.io.Reader, java.io.Writer, java.lang.String, java.lang.String, java.lang.String, org.opensolaris.opengrok.analysis.Definitions, boolean, boolean, java.util.List, org.opensolaris.opengrok.analysis.Scopes)}
     * with {@code in}, {@code out}, {@code urlPrefix}, {@code morePrefix},
     * {@code path}, {@code tags}, {@code limit}, {@code isDefSearch},
     * {@code hits}, and {@code null}.
     * @param in required input stream to be matched
     * @param out optional output stream to write
     * @param urlPrefix prefix for links
     * @param morePrefix to link to more... page
     * @param path path of the file
     * @param tags code definitions.
     * @param limit should the number of matching lines be limited?
     * @param isDefSearch a value indicating whether to always print matched
     * contexts or only when {@link Definitions} tags apply to a line
     * @param hits optional instance
     * @return Did it get any matching context?
     */
    public boolean getContext(Reader in, Writer out, String urlPrefix,
        String morePrefix, String path, Definitions tags,
        boolean limit, boolean isDefSearch, List<Hit> hits) {
        return getContext(in, out, urlPrefix, morePrefix, path, tags, limit, isDefSearch, hits, null);
    }

    /**
     * Look for context for this instance's initialized query in the specified
     * input stream, and output according to the parameters.
     * @param in required input stream to be matched (closed on return)
     * @param out optional output stream to write
     * @param urlPrefix prefix for links
     * @param morePrefix to link to more... page
     * @param path path of the file
     * @param tags code definitions.
     * @param limit should the number of matching lines be limited?
     * @param isDefSearch a value indicating whether to always print matched
     * contexts or only when {@link Definitions} tags apply to a line
     * @param hits optional instance
     * @param scopes optional instance to read
     * @return Did it get any matching context?
     */
    public boolean getContext(Reader in, Writer out, String urlPrefix,
            String morePrefix, String path, Definitions tags,
            boolean limit, boolean isDefSearch, List<Hit> hits, Scopes scopes) {

        if (in == null) {
            throw new IllegalArgumentException("`in' is null");
        }

        if (m == null) {
            IOUtils.close(in);
            return false;
        }
        boolean anything = false;
        TreeMap<Integer, TagDesc> matchingTags = null;
        String urlPrefixE =
                (urlPrefix == null) ? "" : Util.URIEncodePath(urlPrefix);
        String pathE = Util.URIEncodePath(path);
        if (tags != null) {
            matchingTags = new TreeMap<>();
            getContextTags(matchingTags, tags, scopes);
        }

        int charsRead = 0;
        boolean truncated = false;

        boolean lim = limit;
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (!env.isQuickContextScan()) {
            lim = false;
        }

        if (lim) {
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
                for (int i = 0; i < m.length; i++) {
                    matchState = m[i].match(token);
                    if (matchState == LineMatcher.MATCHED) {
                        if (!isDefSearch) {
                            tokens.printContext();
                        } else if (tokens.tags.containsKey(tokens.markedLine)) {
                            tokens.printContext();
                        }
                        matchedLines++;
                        //out.write("<br> <i>Matched " + token + " maxlines = " + matchedLines + "</i><br>");
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
            if (lim && (truncated || matchedLines == limit_max_lines) &&
                    out != null) {
                out.write("<a href=\"" + Util.URIEncodePath(morePrefix) + pathE + "?" + queryAsURI + "\">[all...]</a>");
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

    /**
     * Gets matching, reportable hits from the specified {@code tags} instance.
     * @param hits a defined instance to write
     * @param path a defined instance to use for hit filenames
     * @param tags a defined instance to read
     * @param scopes optional scopes instance
     * @return {@code true} if any tags were put to {@code hits}
     */
    public boolean getContextHits(List<Hit> hits, String path,
        Definitions tags, Scopes scopes) {

        Map<Integer, TagDesc> matchingTags = new TreeMap<>();
        boolean ret = getContextTags(matchingTags, tags, scopes);

        for (Map.Entry<Integer, TagDesc> entry : matchingTags.entrySet()) {
            TagDesc desc = entry.getValue();
            Hit hit = makeHit(path, desc);
            hits.add(hit);
        }
        return ret;
    }

    /**
     * Gets matching, reportable tags from the specified {@code tags} instance.
     * @param matchingTags a defined instance to write
     * @param tags a defined instance to read
     * @param scopes optional scopes instance
     * @return {@code true} if any tags were put to {@code matchingTags}
     */
    public boolean getContextTags(Map<Integer, TagDesc> matchingTags,
        Definitions tags, Scopes scopes) {

        if (m == null) {
            return false;
        }

        boolean anything = false;

        for (Definitions.Tag tag : tags.getTags()) {
            for (LineMatcher m1 : m) {
                if (m1.match(tag.symbol) == LineMatcher.MATCHED) {
                    String scope = null;
                    if (scopes != null) {
                        Scope scp = scopes.getScope(tag.line);
                        scope = scp.getName() + "()";
                    }

                    TagDesc desc = new TagDesc(tag.symbol,
                        Integer.toString(tag.line), tag.type, tag.text, scope);
                    matchingTags.put(tag.line, desc);
                    anything = true;
                    break;
                }
            }
        }

        return anything;
    }

    /**
     * Converts the specified {@code desc} into a {@link Hit}.
     * @param path defined instance
     * @param desc defined instance
     * @return defined instance
     */
    private Hit makeHit(String path, TagDesc desc) {
        return new Hit(path, Util.htmlize(desc.text).replace(desc.symbol,
            "<b>" + desc.symbol + "</b>"), desc.lineno, false, alt);
    }
}
