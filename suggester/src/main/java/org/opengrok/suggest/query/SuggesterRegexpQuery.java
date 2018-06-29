package org.opengrok.suggest.query;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.RegexpQuery;

import java.io.IOException;

public class SuggesterRegexpQuery extends RegexpQuery implements SuggesterQuery {

    public SuggesterRegexpQuery(final Term term) {
        super(term);
    }

    @Override
    public TermsEnum getTermsEnumForSuggestions(final Terms terms) throws IOException {
        if (terms == null) {
            return TermsEnum.EMPTY;
        }
        return super.getTermsEnum(terms);
    }

    @Override
    public int length() {
        return getRegexp().text().length();
    }

}
