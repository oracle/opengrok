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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class SuggesterUtilsTest {

    @Test
    public void intoTermsTest() {
        Term t = new Term("test", "test");

        Query q = new TermQuery(t);

        List<Term> terms = SuggesterUtils.intoTerms(q);

        assertEquals(1, terms.size());
        assertEquals(t, terms.get(0));
    }

    @Test
    public void intoTermsComplexTest() {
        Term t = new Term("test", "term");

        BooleanQuery q = new BooleanQuery.Builder()
                .add(new TermQuery(t), BooleanClause.Occur.MUST)
                .add(new PhraseQuery("test", "term1", "term2"), BooleanClause.Occur.MUST)
                .build();

        List<Term> terms = SuggesterUtils.intoTerms(q);

        assertEquals(3, terms.size());
        assertThat(terms, containsInAnyOrder(t, new Term("test", "term1"),
                new Term("test", "term2")));
    }

    @Test
    public void intoTermExceptPhraseQueryTest() {
        Term t = new Term("test", "term");
        BooleanQuery q = new BooleanQuery.Builder()
                .add(new TermQuery(t), BooleanClause.Occur.MUST)
                .add(new PhraseQuery("test", "term1", "term2"), BooleanClause.Occur.MUST)
                .build();

        List<Term> terms = SuggesterUtils.intoTermsExceptPhraseQuery(q);

        assertEquals(1, terms.size());
        assertEquals(t, terms.get(0));
    }

}
