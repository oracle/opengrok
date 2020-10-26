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
 */
package org.opengrok.suggest.query;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.Operations;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Query for possible suggestions of {@link TermRangeQuery}.
 */
public class SuggesterRangeQuery extends TermRangeQuery implements SuggesterQuery {

    private static final Logger logger = Logger.getLogger(SuggesterRangeQuery.class.getName());

    private final SuggestPosition suggestPosition;

    /**
     * @param field term field
     * @param lowerTerm term on the left side of the range query
     * @param upperTerm term on the right side of the range query
     * @param includeLower if the lower term should be included in the result
     * @param includeUpper if the upper term should be included in the result
     * @param suggestPosition if the suggestions are for the lower or upper term
     */
    public SuggesterRangeQuery(
            final String field,
            final BytesRef lowerTerm,
            final BytesRef upperTerm,
            final boolean includeLower,
            final boolean includeUpper,
            final SuggestPosition suggestPosition
    ) {
        super(field, lowerTerm, upperTerm, includeLower, includeUpper);

        this.suggestPosition = suggestPosition;
        if (suggestPosition == null) {
            throw new IllegalArgumentException("Suggest position cannot be null");
        }
    }

    /** {@inheritDoc} */
    @Override
    public TermsEnum getTermsEnumForSuggestions(final Terms terms) {
        if (terms == null) {
            return TermsEnum.EMPTY;
        }

        BytesRef prefix = getPrefix();
        if (prefix != null) {
            Automaton prefixAutomaton = PrefixQuery.toAutomaton(prefix);

            Automaton finalAutomaton;
            if (suggestPosition == SuggestPosition.LOWER) {
                Automaton binaryInt = Automata.makeBinaryInterval(
                        getLowerTerm(), includesLower(), getUpperTerm(), includesUpper());

                finalAutomaton = Operations.intersection(binaryInt, prefixAutomaton);
            } else {
                Automaton binaryInt = Automata.makeBinaryInterval(null, true, getLowerTerm(), !includesLower());

                finalAutomaton = Operations.minus(prefixAutomaton, binaryInt, Integer.MIN_VALUE);
            }

            CompiledAutomaton compiledAutomaton = new CompiledAutomaton(finalAutomaton);
            try {
                return compiledAutomaton.getTermsEnum(terms);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not compile automaton for range suggestions", e);
            }
        }

        return TermsEnum.EMPTY;
    }

    private BytesRef getPrefix() {
        if (suggestPosition == SuggestPosition.LOWER) {
            return getLowerTerm();
        } else {
            return getUpperTerm();
        }
    }

    /** {@inheritDoc} */
    @Override
    public int length() {
        BytesRef prefix = getPrefix();
        if (prefix == null) {
            return 0;
        }
        return prefix.utf8ToString().length();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SuggesterRangeQuery that = (SuggesterRangeQuery) o;
        return suggestPosition == that.suggestPosition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), suggestPosition);
    }

    public enum SuggestPosition {
        LOWER, UPPER
    }

}
