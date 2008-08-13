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
package org.opensolaris.opengrok.search.context;

import java.util.HashSet;
import java.util.ArrayList;
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
    private HashSet<String> terms;
    private String term;
    private ArrayList<LineMatcher> matchers;
    private Set fields;
    /**
     * Get the terms from a query and returs a list of DFAs which match
     * a stream of tokens
     *
     * @param query
     * @return list of LineMatching DFAs
     */
    public final LineMatcher[] getMatchers(Query query, Set fields) {
        terms = new HashSet<String>();
        matchers = new ArrayList<LineMatcher>();
        this.fields = fields;
        getTerms(query);
        if(terms.size() == 1) {
            //System.out.println("found SingleTokenMatcher" + term);
            matchers.add(0, new SingleTokenMatcher(term));
        } else if (terms.size() > 1) {
            matchers.add(0, new TokenSetMatcher(terms));
        }
        if (matchers.size() == 0) {
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
    
    private final void getBooleans(BooleanQuery query) {
        BooleanClause[] queryClauses = query.getClauses();
        for (int i = 0; i < queryClauses.length; i++) {
            if (!queryClauses[i].isProhibited()) {
                getTerms(queryClauses[i].getQuery());
            }
        }
    }
    
    private  final void getPhrases(PhraseQuery query) {
        Term[] queryTerms = query.getTerms();
        if(queryTerms.length > 0 && fields.contains(queryTerms[0].field())){
            String[] termsArray = new String[queryTerms.length];
            for (int i = 0; i < queryTerms.length; i++) {
                termsArray[i] = queryTerms[i].text().toLowerCase();
            }
            matchers.add(new PhraseMatcher(termsArray));
        }
    }
    
    private final void getTerm(TermQuery query) {
        if(fields.contains(query.getTerm().field())) {
            terms.add(term = query.getTerm().text().toLowerCase());
        }
    }
    
    private final void getWildTerm(WildcardQuery query) {
        if(fields.contains(query.getTerm().field())) {
            matchers.add(new WildCardMatcher(query.getTerm().text().toLowerCase()));
        }
    }
    private final void getPrefix(PrefixQuery query) {
        if(fields.contains(query.getPrefix().field())) {
            matchers.add(new PrefixMatcher(query.getPrefix().text().toLowerCase()));
        }
    }
}
