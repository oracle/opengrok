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
import java.util.logging.Level;
import java.util.logging.Logger;

public class SuggesterRangeQuery extends TermRangeQuery implements SuggesterQuery {

    private static final Logger logger = Logger.getLogger(SuggesterRangeQuery.class.getName());

    private final SuggestPosition suggestPosition;

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

    @Override
    public int length() {
        return 0;
    }

    private BytesRef getPrefix() {
        if (suggestPosition == SuggestPosition.LOWER) {
            return getLowerTerm();
        } else {
            return getUpperTerm();
        }
    }

    public enum SuggestPosition {
        LOWER, UPPER
    }

}
