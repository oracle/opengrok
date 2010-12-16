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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * Utility class used to extract the terms used in a query
 * This class will not find terms for MultiTermQuery, RangeQuery and PrefixQuery classes
 * so the caller must pass a rewritten query (see query.rewrite) to obtain a list of
 * expanded terms.
 *
 */
public final class QueryMatchers {
    private Set<String> caseSensitiveTerms;
    private Set<String> caseInsensitiveTerms;
    private List<LineMatcher> matchers;
    private Map<String, Boolean> fields;
    /**
     * Get the terms from a query and returs a list of DFAs which match
     * a stream of tokens
     *
     * @param query the query to generate matchers for
     * @param fields a map whose keys tell which fields to create matchers for,
     * and whose values tell if the field is case insensitive (true) or
     * case sensitive (false)
     * @return list of LineMatching DFAs
     */
    public LineMatcher[] getMatchers(Query query, Map<String, Boolean> fields) {
        caseSensitiveTerms = new HashSet<String>();
        caseInsensitiveTerms = new HashSet<String>();
        matchers = new ArrayList<LineMatcher>();
        this.fields = fields;
        getTerms(query);
        if (!caseSensitiveTerms.isEmpty()) {
            matchers.add(0, new TokenSetMatcher(caseSensitiveTerms, false));
        }
        if (!caseInsensitiveTerms.isEmpty()) {
            matchers.add(0, new TokenSetMatcher(caseInsensitiveTerms, true));
        }
        if (matchers.isEmpty()) {
            return null;
        }
        LineMatcher[] m = matchers.toArray(new LineMatcher[matchers.size()]);
        return (m);
    }
    
    private void getTerms(Query query) {
        if (query instanceof BooleanQuery) {
            getBooleans((BooleanQuery) query);
        } else if (query instanceof PhraseQuery) {
            getPhrases((PhraseQuery) query);
        } else if (query instanceof WildcardQuery) {
            getWildTerm((WildcardQuery) query);
        } else if (query instanceof TermQuery) {
            getTerm((TermQuery) query);
        } else if (query instanceof PrefixQuery) {
            getPrefix((PrefixQuery) query);
        }
    }
    
    private void getBooleans(BooleanQuery query) {
        BooleanClause[] queryClauses = query.getClauses();
        for (int i = 0; i < queryClauses.length; i++) {
            if (!queryClauses[i].isProhibited()) {
                getTerms(queryClauses[i].getQuery());
            }
        }
    }
    
    private void getPhrases(PhraseQuery query) {
        Term[] queryTerms = query.getTerms();
        if (queryTerms.length > 0 && useTerm(queryTerms[0])) {
            boolean caseInsensitive = isCaseInsensitive(queryTerms[0]);
            String[] termsArray = new String[queryTerms.length];
            for (int i = 0; i < queryTerms.length; i++) {
                termsArray[i] = queryTerms[i].text();
            }
            matchers.add(new PhraseMatcher(termsArray, caseInsensitive));
        }
    }
    
    private void getTerm(TermQuery query) {
        Term term = query.getTerm();
        if (useTerm(term)) {
            String text = term.text();
            if (isCaseInsensitive(term)) {
                caseInsensitiveTerms.add(text);
            } else {
                caseSensitiveTerms.add(text);
            }
        }
    }
    
    private void getWildTerm(WildcardQuery query) {
        Term term = query.getTerm();
        if (useTerm(term)) {
            matchers.add(
                    new WildCardMatcher(term.text(), isCaseInsensitive(term)));
        }
    }

    private void getPrefix(PrefixQuery query) {
        Term term = query.getPrefix();
        if (useTerm(term)) {
            matchers.add(
                    new PrefixMatcher(term.text(), isCaseInsensitive(term)));
        }
    }

    /**
     * Check whether a matcher should be created for a term.
     */
    private boolean useTerm(Term term) {
        return fields.keySet().contains(term.field());
    }

    /**
     * Check if a term should be matched in a case-insensitive manner. Should
     * only be called on terms for which {@link #useTerm(Term)} returns true.
     */
    private boolean isCaseInsensitive(Term term) {
        return fields.get(term.field());
    }
}
