package org.opengrok.suggest.query;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.util.AttributeSource;

import java.io.IOException;

public class SuggesterFuzzyQuery extends FuzzyQuery implements SuggesterQuery {

    public SuggesterFuzzyQuery(final Term term, final int maxEdits, final int prefixLength) {
        super(term, maxEdits, prefixLength);
    }

    @Override
    public TermsEnum getTermsEnumForSuggestions(final Terms terms) throws IOException {
        return getTermsEnum(terms, new AttributeSource());
    }

    @Override
    public int length() {
        return getTerm().text().length();
    }

}
