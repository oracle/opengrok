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
 * Copyright 2010 Sun Micosystems.  All rights reserved.
 * Use is subject to license terms.
 *
 * Portions Copyright 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

/**
 * Helper class that builds a Lucene query based on provided search terms for
 * the different fields.
 */
public class QueryBuilder {
    final static String FULL = "full";
    final static String DEFS = "defs";
    final static String REFS = "refs";
    final static String PATH = "path";
    final static String HIST = "hist";
    /**
     * A map containing the query text for each field. (We use a sorted map here
     * only because we have tests that check the generated query string. If we
     * had used a hash map, the order of the terms could have varied between
     * platforms and it would be harder to test.)
     */
    private final Map<String, String> queries = new TreeMap<String, String>();

    /**
     * Set search string for the "full" field.
     * @param freetext query string to set
     * @return this instance
     */
    public QueryBuilder setFreetext(String freetext) {
        return addQueryText(FULL, freetext);
    }
    /**
     * Get search string for the "full" field.
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getFreetext() {
        return getQueryText(FULL);
    }

    /**
     * Set search string for the "defs" field.
     * @param defs query string to set
     * @return this instance
     */
    public QueryBuilder setDefs(String defs) {
        return addQueryText(DEFS, defs);
    }
    /**
     * Get search string for the "full" field.
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getDefs() {
        return getQueryText(DEFS);
    }

    /**
     * Set search string for the "refs" field.
     * @param refs query string to set
     * @return this instance
     */
    public QueryBuilder setRefs(String refs) {
        return addQueryText(REFS, refs);
    }
    /**
     * Get search string for the "refs" field.
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getRefs() {
        return getQueryText(REFS);
    }

    /** Set search string for the "path" field.
     * @param path query string to set
     * @return this instance
     */
    public QueryBuilder setPath(String path) {
        return addQueryText(PATH, path);
    }
    /**
     * Get search string for the "path" field.
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getPath() {
        return getQueryText(PATH);
    }

    /**
     * Set search string for the "hist" field.
     * @param hist query string to set
     * @return this instance
     */
    public QueryBuilder setHist(String hist) {
        return addQueryText(HIST, hist);
    }
    /**
     * Get search string for the "hist" field.
     * @return {@code null} if not set, the query string otherwise.
     */
    public String getHist() {
        return getQueryText(HIST);
    }

    /**
     * Get a map containing the query text for each of the fields that have
     * been set.
     * @return a possible empty map.
     */
    public Map<String, String> getQueries() {
        return Collections.unmodifiableMap(queries);
    }

    /**
     * Get the number of query fields set.
     * @return the current number of fields with a none-empty query string.
     */
    public int getSize() {
        return queries.size();
    }

    /**
     * Build a new query based on the query text that has been passed in to this
     * builder.
     *
     * @return a query, or {@code null} if no query text is available.
     * @throws ParseException  if the query text cannot be parsed
     */
    public Query build() throws ParseException {
        if (queries.isEmpty()) {
            // We don't have any text to parse
            return null;
        }
        // Parse each of the query texts separately
        ArrayList<Query> queryList = new ArrayList<Query>(queries.size());
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
        BooleanQuery combinedQuery = new BooleanQuery();
        for (Query query : queryList) {
            if (query instanceof BooleanQuery) {
                BooleanQuery boolQuery = (BooleanQuery) query;
                if (hasClause(boolQuery, Occur.SHOULD)
                    && !hasClause(boolQuery, Occur.MUST))
                {
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
        return combinedQuery;
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
        return FULL.equals(field)
            // The free text field may contain terms qualified with other
            // field names, so we don't escape single colons.
            ? query.replace("::", "\\:\\:")
            // Other fields shouldn't use qualified terms, so escape colons
            // so that we can search for them.
            : query.replace(":", "\\:");
    }

    /**
     * Build a subquery against one of the fields.
     *
     * @param field the field to build the query against
     * @param queryText the query text
     * @return a parsed query
     * @throws ParseException   if the query text cannot be parsed
     */
    private Query buildQuery(String field, String queryText)
        throws ParseException
    {
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
