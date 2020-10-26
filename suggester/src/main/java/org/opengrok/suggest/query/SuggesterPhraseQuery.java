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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.query.customized.CustomPhraseQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Query for possible suggestions of {@link org.apache.lucene.search.PhraseQuery}. It is divided into
 * {@link CustomPhraseQuery} which represents the bare {@link org.apache.lucene.search.PhraseQuery} and
 * {@link SuggesterQuery} which represent the query for suggestions.
 */
public class SuggesterPhraseQuery extends Query {

    private enum SuggestPosition {
        START, MIDDLE, END;

        private static SuggestPosition from(final int pos, final int size) {
            if (pos == 0) {
                return SuggestPosition.START;
            } else if (pos == size - 1) {
                return SuggestPosition.END;
            } else {
                return SuggestPosition.MIDDLE;
            }
        }
    }

    private CustomPhraseQuery phraseQuery;

    private SuggesterQuery suggesterQuery;

    /**
     * @param field term field
     * @param identifier unique String which identifies the token for which the suggestions should be made
     * @param tokens all the tokens of the phrase query
     * @param slop word Levenshtein's distance
     */
    public SuggesterPhraseQuery(
            final String field,
            final String identifier,
            final List<String> tokens,
            final int slop
    ) {
        String prefix = null;
        int pos = -1;
        int i = 0;

        List<Integer> positions = new ArrayList<>();
        List<Term> terms = new ArrayList<>();

        for (String token : tokens) {
            if (token.contains(identifier)) {
                prefix = token.replace(identifier, "");
                pos = i;
            } else {
                positions.add(i);
                terms.add(new Term(field, token));
            }
            i++;
        }

        if (pos < 0 || pos > tokens.size()) {
            throw new IllegalStateException();
        }

        SuggestPosition p = SuggestPosition.from(pos, tokens.size());

        if (p == SuggestPosition.START) {
            phraseQuery = new CustomPhraseQuery(slop, field, tokens.stream().filter(t -> !t.contains(identifier)).toArray(String[]::new));
            phraseQuery.setOffset(-1);
        } else if (p == SuggestPosition.MIDDLE) {
            phraseQuery = new CustomPhraseQuery(slop, terms.toArray(new Term[0]), positions.stream().mapToInt(in -> in).toArray());
            phraseQuery.setOffset(pos);
        } else {
            phraseQuery = new CustomPhraseQuery(slop, field, tokens.stream().filter(t -> !t.contains(identifier)).toArray(String[]::new));
            phraseQuery.setOffset(tokens.size() - 1);
        }

        suggesterQuery = new SuggesterPrefixQuery(new Term(field, prefix));
    }

    public SuggesterQuery getSuggesterQuery() {
         return suggesterQuery;
    }

    public CustomPhraseQuery getPhraseQuery() {
        return phraseQuery;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SuggesterPhraseQuery that = (SuggesterPhraseQuery) o;
        return Objects.equals(phraseQuery, that.phraseQuery) &&
                Objects.equals(suggesterQuery, that.suggesterQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phraseQuery, suggesterQuery);
    }

    @Override
    public String toString(final String field) {
        return "SuggesterPhraseQuery{" +
                "phraseQuery=" + phraseQuery +
                ", suggesterQuery=" + suggesterQuery +
                '}';
    }

    @Override
    public Query rewrite(final IndexReader reader) {
        return phraseQuery.rewrite(reader);
    }

}
