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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.suggest.query;

import org.apache.lucene.index.Term;
import org.junit.Test;
import org.opengrok.suggest.query.customized.CustomPhraseQuery;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class SuggesterPhraseQueryTest {

    @Test
    public void prefixQueryTest() {
        SuggesterPhraseQuery q = new SuggesterPhraseQuery("test", "ident",
                Arrays.asList("one", "two", "tident"), 0);

        SuggesterPrefixQuery prefixQuery = (SuggesterPrefixQuery) q.getSuggesterQuery();

        assertEquals("t", prefixQuery.getPrefix().text());
    }

    @Test
    public void phraseQueryTest() throws Exception {
        SuggesterPhraseQuery q = new SuggesterPhraseQuery("test", "ident",
                Arrays.asList("one", "two", "tident"), 0);

        CustomPhraseQuery query = q.getPhraseQuery();

        assertEquals(2, query.getOffset());

        Term[] terms = getTerms(query);

        assertEquals("one", terms[0].text());
        assertEquals("two", terms[1].text());
    }

    private Term[] getTerms(final CustomPhraseQuery query) throws Exception {
        Field f = CustomPhraseQuery.class.getDeclaredField("terms");
        f.setAccessible(true);
        return (Term[]) f.get(query);
    }

}
