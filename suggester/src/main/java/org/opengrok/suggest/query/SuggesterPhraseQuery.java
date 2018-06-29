package org.opengrok.suggest.query;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.query.customized.MyPhraseQuery;

import java.util.ArrayList;
import java.util.List;

public class SuggesterPhraseQuery extends Query {

    private enum SuggestPosition {
        START, MIDDLE, END
    }

    private MyPhraseQuery phraseQuery;

    private SuggesterQuery suggesterQuery;

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

        SuggestPosition p = getPosition(pos, tokens.size());

        if (p == SuggestPosition.START) {
            phraseQuery = new MyPhraseQuery(slop, field, tokens.stream().filter(t -> !t.contains(identifier)).toArray(String[]::new));
            phraseQuery.offset = -1;
        } else if (p == SuggestPosition.MIDDLE) {
            phraseQuery = new MyPhraseQuery(slop, terms.toArray(new Term[0]), positions.stream().mapToInt(in -> in).toArray());
            phraseQuery.offset = pos;
        } else {
            phraseQuery = new MyPhraseQuery(slop, field, tokens.stream().filter(t -> !t.contains(identifier)).toArray(String[]::new));
            phraseQuery.offset = tokens.size() - 1;
        }

        suggesterQuery = new SuggesterPrefixQuery(new Term(field, prefix));
    }

    public SuggesterQuery getSuggesterQuery() {
         return suggesterQuery;
    }

    public MyPhraseQuery getPhraseQuery() {
        return phraseQuery;
    }

    @Override
    public String toString(String s) {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public Query rewrite(IndexReader reader) {
        return phraseQuery.rewrite(reader);
    }

    private static SuggestPosition getPosition(int pos, int size) {
        if (pos == 0) {
            return SuggestPosition.START;
        } else if (pos == size - 1) {
            return SuggestPosition.END;
        } else {
            return SuggestPosition.MIDDLE;
        }
    }

}
