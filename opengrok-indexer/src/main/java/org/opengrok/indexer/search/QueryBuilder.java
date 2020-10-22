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
 * Copyright (c) 2010, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

/**
 * Helper class that builds a Lucene query based on provided search terms for
 * the different fields.
 */
public class QueryBuilder {

    /**
     * Fields we use in lucene: public ones.
     */
    public static final String FULL = "full";
    public static final String DEFS = "defs";
    public static final String REFS = "refs";
    public static final String PATH = "path";
    public static final String HIST = "hist";
    public static final String TYPE = "type";
    public static final String SCOPES = "scopes";
    public static final String NUML = "numl";
    public static final String LOC = "loc";
    /**
     * Fields we use in lucene: internal ones.
     */
    public static final String U = "u";
    public static final String TAGS = "tags";
    public static final String T = "t";
    public static final String FULLPATH = "fullpath";
    public static final String DIRPATH = "dirpath";
    public static final String PROJECT = "project";
    public static final String DATE = "date";
    public static final String OBJUID = "objuid"; // object UID
    public static final String OBJSER = "objser"; // object serialized
    public static final String OBJVER = "objver"; // object version

    protected static final List<String> searchFields = Arrays.asList(FULL, DEFS, REFS, PATH, HIST);
    private static final HashSet<String> searchFieldsSet = new HashSet<>(searchFields);

    /** Used for paths, so SHA-1 is completely sufficient. */
    private static final String DIRPATH_HASH_ALGORITHM = "SHA-1";

    /**
     * A map containing the query text for each field. (We use a sorted map here
     * only because we have tests that check the generated query string. If we
     * had used a hash map, the order of the terms could have varied between
     * platforms and it would be harder to test.)
     */
    private final Map<String, String> queries = new TreeMap<>();

    public static List<String> getSearchFields() {
        return Collections.unmodifiableList(searchFields);
    }

    /**
     * Gets a value indicating if the specified {@code fieldName} is a valid
     * search field.
     */
    public static boolean isSearchField(String fieldName) {
        return searchFieldsSet.contains(fieldName);
    }

    /**
     * Sets the instance to the state of {@code other}.
     * @param other a defined instance
     * @return {@code this}
     */
    public QueryBuilder reset(QueryBuilder other) {
        if (other == null) {
            throw new IllegalArgumentException("other is null");
        }
        if (this != other) {
            queries.clear();
            queries.putAll(other.queries);
        }
        return this;
    }

    /**
     * Set search string for the {@link #FULL} field.
     *
     * @param freetext query string to set
     * @return this instance
     */
    public QueryBuilder setFreetext(String freetext) {
        return addQueryText(FULL, freetext);
    }

    /**
     * Get search string for the {@link #FULL} field.
     *
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getFreetext() {
        return getQueryText(FULL);
    }

    /**
     * Set search string for the {@link #DEFS} field.
     *
     * @param defs query string to set
     * @return this instance
     */
    public QueryBuilder setDefs(String defs) {
        return addQueryText(DEFS, defs);
    }

    /**
     * Get search string for the {@link #FULL} field.
     *
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getDefs() {
        return getQueryText(DEFS);
    }

    /**
     * Set search string for the {@link #REFS} field.
     *
     * @param refs query string to set
     * @return this instance
     */
    public QueryBuilder setRefs(String refs) {
        return addQueryText(REFS, refs);
    }

    /**
     * Get search string for the {@link #REFS} field.
     *
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getRefs() {
        return getQueryText(REFS);
    }

    /**
     * Set search string for the {@link #PATH} field.
     *
     * @param path query string to set
     * @return this instance
     */
    public QueryBuilder setPath(String path) {
        return addQueryText(PATH, path);
    }

    /**
     * Get search string for the {@link #PATH} field.
     *
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getPath() {
        return getQueryText(PATH);
    }

    /**
     * Set search string for the {@link #DIRPATH} field.
     * @param path query string to set
     * @return this instance
     */
    public QueryBuilder setDirPath(String path) {
        String normalizedPath = normalizeDirPath(path);
        return addQueryText(DIRPATH, normalizedPath);
    }

    /**
     * Get search string for the {@link #DIRPATH} field.
     * @return {@code null} if not set; the query string otherwise.
     */
    public String getDirPath() {
        return getQueryText(DIRPATH);
    }

    /**
     * Transform {@code path} to ensure any {@link File#separatorChar} is
     * represented as '/', that there is a trailing '/', and then to hash using
     * SHA-1 and formatted in a private encoding using only letters [g-u].
     * @param path a defined value
     * @return a defined, transformed value
     */
    public static String normalizeDirPath(String path) {
        String norm1 = path.replace(File.separatorChar, '/');
        String norm2 = norm1.endsWith("/") ? norm1 : norm1 + "/";

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(DIRPATH_HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            return norm2;
        }
        byte[] hash = digest.digest(norm2.getBytes(StandardCharsets.UTF_8));

        StringBuilder encodedString = new StringBuilder();
        for (byte b : hash) {
            int v0 = (0xF0 & b) >> 4;
            int v1 = 0xF & b;
            char c0 = (char) ('g' + v0);
            char c1 = (char) ('g' + v1);
            encodedString.append(c0);
            encodedString.append(c1);
        }
        return encodedString.toString();
    }

    /**
     * Set search string for the {@link #HIST} field.
     *
     * @param hist query string to set
     * @return this instance
     */
    public QueryBuilder setHist(String hist) {
        return addQueryText(HIST, hist);
    }

    /**
     * Get search string for the {@link #HIST} field.
     *
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getHist() {
        return getQueryText(HIST);
    }

    /**
     * Set search string for the {@link #TYPE} field.
     *
     * @param type query string to set
     * @return this instance
     */
    public QueryBuilder setType(String type) {
        return addQueryText(TYPE, type);
    }

    /**
     * Get search string for the {@link #TYPE} field.
     *
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getType() {
        return getQueryText(TYPE);
    }

    /**
     * Get a map containing the query text for each of the fields that have been
     * set.
     *
     * @return a possible empty map.
     */
    public Map<String, String> getQueries() {
        return Collections.unmodifiableMap(queries);
    }

    /**
     * Gets a list of fields from {@link #getQueries()} which are extracted
     * from source text and which therefore can be used for context
     * presentations -- in the order of most specific to least.
     * @return a defined, possibly-empty list
     */
    public List<String> getContextFields() {
        List<String> fields = new ArrayList<>(queries.size());
        /**
         * setFreetext() allows query fragments that specify a field name with
         * a colon (e.g., "defs:ensure_cache" in the "Full Search" box), so the
         * context fields (i.e., the result of this method) are not just the
         * keys of `queries' but need a full parsing to be determined.
         */
        Query query;
        try {
            query = build();
        } catch (ParseException ex) {
            return fields;
        }
        String queryString = query.toString("");
        if (queryString.contains(DEFS + ":")) {
            fields.add(DEFS);
        }
        if (queryString.contains(REFS + ":")) {
            fields.add(REFS);
        }
        if (queryString.contains(FULL + ":")) {
            fields.add(FULL);
        }
        return fields;
    }

    /**
     * Get the number of query fields set.
     *
     * @return the current number of fields with a none-empty query string.
     */
    public int getSize() {
        return queries.size();
    }

    /**
     * Used to tell if this search only has the {@link #DEFS} field filled in.
     *
     * @return whether above statement is true or false
     */
    public boolean isDefSearch() {

        return ((getQueryText(FULL) == null)
                && (getQueryText(REFS) == null)
                && (getQueryText(PATH) == null)
                && (getQueryText(HIST) == null)
                && (getQueryText(DIRPATH) == null)
                && (getQueryText(DEFS) != null));
    }

    /**
     * Gets a value indicating if this search only has defined the {@link #PATH}
     * query field.
     */
    public boolean isPathSearch() {
        return ((getQueryText(FULL) == null)
                && (getQueryText(REFS) == null)
                && (getQueryText(PATH) != null)
                && (getQueryText(HIST) == null)
                && (getQueryText(DIRPATH) == null)
                && (getQueryText(DEFS) == null));
    }

    /**
     * Build a new query based on the query text that has been passed in to this
     * builder.
     *
     * @return a query, or {@code null} if no query text is available.
     * @throws ParseException if the query text cannot be parsed
     */
    public Query build() throws ParseException {
        if (queries.isEmpty()) {
            // We don't have any text to parse
            return null;
        }
        // Parse each of the query texts separately
        ArrayList<Query> queryList = new ArrayList<>(queries.size());
        for (Map.Entry<String, String> entry : queries.entrySet()) {
            String field = entry.getKey();
            String queryText = entry.getValue();
            queryList.add(buildQuery(field, escapeQueryString(field, queryText)));
        }
        // If we only have one sub-query, return it directly
        if (queryList.size() == 1) {
            return queryList.get(0);
        }
        // We have multiple subqueries, so let's combine them into a
        // BooleanQuery.
        //
        // If the subquery is a BooleanQuery, we pull out each clause and
        // add it to the outer BooleanQuery so that any negations work on
        // the query as a whole. One exception to this rule: If the query
        // contains one or more Occur.SHOULD clauses and no Occur.MUST
        // clauses, we keep it in a subquery so that the requirement that
        // at least one of the Occur.SHOULD clauses must match (pulling them
        // out would make all of them optional).
        //
        // All other types of subqueries are added directly to the outer
        // query with Occur.MUST.
        BooleanQuery.Builder combinedQuery = new BooleanQuery.Builder();
        for (Query query : queryList) {
            if (query instanceof BooleanQuery) {
                BooleanQuery boolQuery = (BooleanQuery) query;
                if (hasClause(boolQuery, Occur.SHOULD)
                        && !hasClause(boolQuery, Occur.MUST)) {
                    combinedQuery.add(query, Occur.MUST);
                } else {
                    for (BooleanClause clause : boolQuery) {
                        combinedQuery.add(clause);
                    }
                }
            } else {
                combinedQuery.add(query, Occur.MUST);
            }
        }
        return combinedQuery.build();
    }

    /**
     * Add query text for the specified field.  
     *
     * @param field the field to add query text for
     * @param query the query text to set
     * @return this object
     */
    private QueryBuilder addQueryText(String field, String query) {
        if (query == null || query.isEmpty()) {
            queries.remove(field);
        } else {
            queries.put(field, query);
        }
        return this;
    }

    private String getQueryText(String field) {
        return queries.get(field);
    }

    /**
     * Escape special characters in a query string.
     *
     * @param field the field for which the query string is provided
     * @param query the query string to escape
     * @return the escaped query string
     */
    private String escapeQueryString(String field, String query) {
        StringReader reader = new StringReader(query);
        StringBuilder res = new StringBuilder();
        switch (field) {
            case FULL:
                FullQueryEscaper fesc = new FullQueryEscaper(reader);
                fesc.setOut(res);
                fesc.consume();
                break;
            case PATH:
                if (!(query.startsWith("/") && query.endsWith("/"))) {
                    PathQueryEscaper pesc = new PathQueryEscaper(reader);
                    pesc.setOut(res);
                    pesc.consume();
                    break;
                }
                // FALLTHROUGH
            default:
                DefaultQueryEscaper desc = new DefaultQueryEscaper(reader);
                desc.setOut(res);
                desc.consume();
        }
        return res.toString();
    }

    /**
     * Build a subquery against one of the fields.
     *
     * @param field the field to build the query against
     * @param queryText the query text
     * @return a parsed query
     * @throws ParseException if the query text cannot be parsed
     */
    protected Query buildQuery(String field, String queryText)
            throws ParseException {
        return new CustomQueryParser(field).parse(queryText);
    }

    /**
     * Check if a BooleanQuery contains a clause of a given occur type.
     *
     * @param query the query to check
     * @param occur the occur type to check for
     * @return whether or not the query contains a clause of the specified type
     */
    private boolean hasClause(BooleanQuery query, Occur occur) {
        for (BooleanClause clause : query) {
            if (clause.getOccur().equals(occur)) {
                return true;
            }
        }
        return false;
    }
}
