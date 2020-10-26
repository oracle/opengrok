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

import java.io.IOException;

/**
 * Query that selects the terms that could be used as suggestions.
 */
public interface SuggesterQuery {

    /**
     * @return field for which the query is
     */
    String getField();

    /**
     * Returns terms that satisfy this query.
     * @param terms terms from which to filter the ones that satisfy this query
     * @return terms enum of the terms that satisfy this query
     * @throws IOException if an error occurred
     */
    TermsEnum getTermsEnumForSuggestions(Terms terms) throws IOException;

    /**
     * Length of the query. Used for determining whether query is longer than specified in configuration.
     * @return query length
     */
    int length();

}
