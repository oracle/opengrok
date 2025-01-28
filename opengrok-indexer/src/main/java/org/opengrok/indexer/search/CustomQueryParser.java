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
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import java.util.Locale;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.opengrok.indexer.analysis.CompatibleAnalyser;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

/**
 * A custom query parser for OpenGrok.
 */
public class CustomQueryParser extends QueryParser {

    /**
     * Create a query parser customized for OpenGrok.
     *
     * @param field default field for unqualified query terms
     */
    @SuppressWarnings("this-escape")
    public CustomQueryParser(String field) {
        super(field, new CompatibleAnalyser());
        setDefaultOperator(AND_OPERATOR);
        setAllowLeadingWildcard(
                RuntimeEnvironment.getInstance().isAllowLeadingWildcard());
        // Convert terms to lower case manually to prevent changing the case
        // if the field is case sensitive.
        // since lucene 7.0.0 below is in place so every class that
        // extends Analyser must normalize the text by itself
        /*
## AnalyzingQueryParser removed (LUCENE-7355)

The functionality of AnalyzingQueryParser has been folded into the classic
QueryParser, which now passes terms through Analyzer#normalize when generating
queries.

## CommonQueryParserConfiguration.setLowerCaseExpandedTerms removed (LUCENE-7355)

Expanded terms are now normalized through
Analyzer#normalize.
        */

    }

    /**
     * Is this field case sensitive?
     *
     * @param field name of the field to check
     * @return {@code true} if the field is case sensitive, {@code false}
     * otherwise
     */
    protected static boolean isCaseSensitive(String field) {
        // Only definition search and reference search are case sensitive
        return QueryBuilder.DEFS.equals(field)
                || QueryBuilder.REFS.equals(field);
    }

    /**
     * Get a canonical form of a search term. This will convert the term to
     * lower case if the field is case insensitive.
     *
     * @param field the field to search on
     * @param term the term to search for
     * @return the canonical form of the search term, which matches how it is
     * stored in the index
     */
    // The analyzers use the default locale. They probably should have used
    // a fixed locale, but since they don't, we ignore that PMD warning here.
    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    private static String getCanonicalTerm(String field, String term) {
        return isCaseSensitive(field) ? term : term.toLowerCase(Locale.ROOT);
    }

    // Override the get***Query() methods to lower case the search terms if
    // the field is case sensitive. We don't need to override getFieldQuery()
    // because it uses the analyzer to convert the terms to canonical form.
    @Override
    protected Query getFuzzyQuery(String field, String term, float min)
            throws ParseException {
        return super.getFuzzyQuery(field, getCanonicalTerm(field, term), min);
    }

    @Override
    protected Query getPrefixQuery(String field, String term)
            throws ParseException {
        return super.getPrefixQuery(field, getCanonicalTerm(field, term));
    }

    @Override
    protected Query getRangeQuery(String field, String term1, String term2,
            boolean startinclusive, boolean endinclusive)
            throws ParseException {
        return super.getRangeQuery(
                field,
                getCanonicalTerm(field, term1),
                getCanonicalTerm(field, term2),
                startinclusive,
                endinclusive);
    }

    @Override
    protected Query getWildcardQuery(String field, String term)
            throws ParseException {
        return super.getWildcardQuery(field, getCanonicalTerm(field, term));
    }
}
