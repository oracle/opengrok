package org.opengrok.suggest.query;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;

import java.io.IOException;

public interface SuggesterQuery {

    String getField();

    TermsEnum getTermsEnumForSuggestions(Terms terms) throws IOException;

    int length();

}
