package org.opengrok.suggest.query;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.AttributeSource;

import java.io.IOException;

public class SuggesterWildcardQuery extends WildcardQuery implements SuggesterQuery {

    public SuggesterWildcardQuery(final Term term) {
        super(term);
    }

    @Override
    public TermsEnum getTermsEnumForSuggestions(final Terms terms) throws IOException {
        if (terms == null) {
            return TermsEnum.EMPTY;
        }
        return super.getTermsEnum(terms, new AttributeSource());
    }

    @Override
    public int length() {
        return getTerm().text().length();
    }

}
