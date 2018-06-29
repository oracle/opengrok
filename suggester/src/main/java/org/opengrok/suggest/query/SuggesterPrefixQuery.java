package org.opengrok.suggest.query;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.util.AttributeSource;

import java.io.IOException;

public class SuggesterPrefixQuery extends PrefixQuery implements SuggesterQuery {

    public SuggesterPrefixQuery(final Term prefix) {
        super(prefix);
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
        return getPrefix().text().length();
    }

}
