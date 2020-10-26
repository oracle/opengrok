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

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.RegexpQuery;

import java.io.IOException;

/**
 * Query for possible suggestions of {@link RegexpQuery}.
 */
public class SuggesterRegexpQuery extends RegexpQuery implements SuggesterQuery {

    /**
     * @param term term with regexp symbols
     */
    public SuggesterRegexpQuery(final Term term) {
        super(term);
    }

    /** {@inheritDoc} */
    @Override
    public TermsEnum getTermsEnumForSuggestions(final Terms terms) throws IOException {
        if (terms == null) {
            return TermsEnum.EMPTY;
        }
        return super.getTermsEnum(terms);
    }

    /** {@inheritDoc} */
    @Override
    public int length() {
        return getRegexp().text().length();
    }

}
