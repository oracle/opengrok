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
package org.opengrok.web.api.v1.suggester.query;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.opengrok.suggest.query.SuggesterFuzzyQuery;
import org.opengrok.suggest.query.SuggesterPhraseQuery;
import org.opengrok.suggest.query.SuggesterPrefixQuery;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opengrok.suggest.query.SuggesterRangeQuery;
import org.opengrok.suggest.query.SuggesterRegexpQuery;
import org.opengrok.suggest.query.SuggesterWildcardQuery;
import org.opengrok.indexer.search.CustomQueryParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.apache.lucene.search.BoostAttribute.DEFAULT_BOOST;

/**
 * Used for parsing the text of a query for which suggestions should be retrieved. Decouples the query into 2 parts:
 * {@link SuggesterQuery} for suggestions and ordinary {@link Query} which serves as a dependency of
 * {@link SuggesterQuery}.
 */
class SuggesterQueryParser extends CustomQueryParser {

    private static final Logger logger = Logger.getLogger(SuggesterQueryParser.class.getName());

    private final Set<BooleanClause> suggesterClauses = new HashSet<>();

    private String identifier;

    private SuggesterQuery suggesterQuery;

    private String queryTextWithPlaceholder;

    /**
     * @param field field that is being parsed
     * @param identifier identifier that was inserted into the query to detect the {@link SuggesterQuery}
     */
    SuggesterQueryParser(final String field, final String identifier) {
        super(field);
        this.identifier = identifier;
        // always allow leading wildcard suggestions (even if they are disabled in configuration)
        setAllowLeadingWildcard(true);
    }

    public SuggesterQuery getSuggesterQuery() {
        return suggesterQuery;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getQueryTextWithPlaceholder() {
        return queryTextWithPlaceholder;
    }

    @Override
    protected Query newTermQuery(final Term term, float boost) {
        if (term.text().contains(identifier)) {
            Query q = new SuggesterPrefixQuery(replaceIdentifier(term, identifier));
            this.suggesterQuery = (SuggesterPrefixQuery) q;
            if (boost != DEFAULT_BOOST) {
                q = new BoostQuery(q, boost);
            }
            return q;
        }

        return super.newTermQuery(term, boost);
    }

    private Term replaceIdentifier(final Term term, final String identifier) {
        Term newTerm = new Term(term.field(), term.text().replace(identifier, ""));
        replaceIdentifier(term.field(), term.text());
        return newTerm;
    }

    private void replaceIdentifier(final String field, final String text) {
        this.identifier = text;
        if (!isCaseSensitive(field)) {
            // fixes problem when prefix contains upper case chars for case insensitive field
            queryTextWithPlaceholder = queryTextWithPlaceholder.replaceAll(
                    "(?i)" + Pattern.quote(identifier), identifier);
        }
    }

    @Override
    protected Query getBooleanQuery(final List<BooleanClause> clauses) throws ParseException {

        boolean contains = false;

        for (BooleanClause clause : clauses) {
            for (BooleanClause clause1 : suggesterClauses) {
                if (clause.getQuery().equals(clause1.getQuery())) {
                    contains = true;
                }
            }
        }

        if (contains) {
            List<BooleanClause> reducedList = new ArrayList<>();

            for (BooleanClause clause : clauses) {
                if (clause.getOccur() != BooleanClause.Occur.SHOULD) {
                    reducedList.add(clause);
                } else {
                    for (BooleanClause clause1 : suggesterClauses) {
                        if (clause.getQuery().equals(clause1.getQuery())) {
                            reducedList.add(clause);
                        }
                    }
                }
            }

            return super.getBooleanQuery(reducedList);
        } else {
            return super.getBooleanQuery(clauses);
        }
    }

    @Override
    protected BooleanClause newBooleanClause(final Query q, final BooleanClause.Occur occur) {
        BooleanClause bc;

        if (q instanceof SuggesterPhraseQuery) {
            bc = super.newBooleanClause(((SuggesterPhraseQuery) q).getPhraseQuery(), BooleanClause.Occur.MUST);
            suggesterClauses.add(bc);
        } else if (q instanceof SuggesterQuery) {
            bc = super.newBooleanClause(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
            suggesterClauses.add(bc);
        } else if (q instanceof BooleanQuery) {
            bc = super.newBooleanClause(q, occur);
            for (BooleanClause clause : ((BooleanQuery) q).clauses()) {
                if (suggesterClauses.contains(clause)) {
                    suggesterClauses.add(bc);
                }
            }
        } else {
            bc = super.newBooleanClause(q, occur);
        }

        return bc;
    }

    @Override
    public Query parse(final String query) throws ParseException {
        this.queryTextWithPlaceholder = query;
        return super.parse(query);
    }

    @Override
    protected Query newPrefixQuery(final Term prefix) {
        if (prefix.text().contains(identifier)) {
            SuggesterPrefixQuery q = new SuggesterPrefixQuery(replaceIdentifier(prefix, identifier));
            this.suggesterQuery = q;
            return q;
        }

        return super.newPrefixQuery(prefix);
    }

    @Override
    protected Query newWildcardQuery(final Term t) {
        if (t.text().contains(identifier)) {
            String term = t.text().replace(identifier, "");
            if (term.endsWith("*") && !containsWildcardCharacter(term.substring(0, term.length() - 1))) {
                // the term ends with "*" but contains no other wildcard characters so faster method can be used
                replaceIdentifier(t.field(), t.text());
                term = term.substring(0, term.length() - 1);
                SuggesterPrefixQuery q = new SuggesterPrefixQuery(new Term(t.field(), term));
                this.suggesterQuery = q;
                return q;
            } else {
                SuggesterWildcardQuery q = new SuggesterWildcardQuery(replaceIdentifier(t, identifier));
                replaceIdentifier(t.field(), t.text());
                this.suggesterQuery = q;
                return q;
            }
        }

        return super.newWildcardQuery(t);
    }

    private boolean containsWildcardCharacter(final String s) {
        return s.contains("?") || s.contains("*");
    }

    @Override
    protected Query newFuzzyQuery(final Term term, final float minimumSimilarity, final int prefixLength) {
        if (term.text().contains(identifier)) {
            Term newTerm = replaceIdentifier(term, identifier);

            if (minimumSimilarity < 1) {
                replaceIdentifier(term.field(), term.text() + "~" + minimumSimilarity);
            } else { // similarity greater than 1 must be an integer
                replaceIdentifier(term.field(), term.text() + "~" + ((int) minimumSimilarity));
            }

            @SuppressWarnings("deprecation")
            int numEdits = FuzzyQuery.floatToEdits(minimumSimilarity,
                    newTerm.text().codePointCount(0, newTerm.text().length()));

            SuggesterFuzzyQuery q = new SuggesterFuzzyQuery(newTerm, numEdits, prefixLength);
            this.suggesterQuery = q;
            return q;
        }

        return super.newFuzzyQuery(term, minimumSimilarity, prefixLength);
    }

    @Override
    protected Query newRegexpQuery(final Term regexp) {
        if (regexp.text().contains(identifier)) {
            Term newTerm = replaceIdentifier(regexp, identifier);

            replaceIdentifier(regexp.field(), "/" + regexp.text() + "/");

            SuggesterRegexpQuery q = new SuggesterRegexpQuery(newTerm);
            this.suggesterQuery = q;
            return q;
        }

        return super.newRegexpQuery(regexp);
    }

    @Override
    protected Query newFieldQuery(
            final Analyzer analyzer,
            final String field,
            final String queryText,
            final boolean quoted
    ) throws ParseException {

        if (quoted && queryText.contains(identifier)) {
            List<String> tokens = getAllTokens(analyzer, field, queryText);

            SuggesterPhraseQuery spq = new SuggesterPhraseQuery(field, identifier, tokens, this.getPhraseSlop());
            this.suggesterQuery = spq.getSuggesterQuery();
            replaceIdentifier(field, tokens.stream().filter(t -> t.contains(identifier)).findAny().get());
            return spq;
        }

        return super.newFieldQuery(analyzer, field, queryText, quoted);
    }

    private static List<String> getAllTokens(final Analyzer analyzer, final String field, final String text) {
        List<String> tokens = new LinkedList<>();

        TokenStream ts = null;
        try {
            ts = analyzer.tokenStream(field, text);

            CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);

            ts.reset();
            while (ts.incrementToken()) {
                tokens.add(attr.toString());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not analyze query text", e);
        } finally {
            try {
                if (ts != null) {
                    ts.end();
                    ts.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not close token stream", e);
            }
        }

        return tokens;
    }

    @Override
    protected Query getFieldQuery(final String field, final String queryText, final int slop) throws ParseException {
        // this is a trick because we get slop here just as a parameter and it is not set to the field

        int oldPhraseSlope = getPhraseSlop();

        // set slop to correct value for field query evaluation
        setPhraseSlop(slop);

        Query query = super.getFieldQuery(field, queryText, slop);

        // set slop to the default value
        setPhraseSlop(oldPhraseSlope);

        return query;
    }

    @Override
    protected Query newRangeQuery(
            final String field,
            final String lowerTerm,
            final String upperTerm,
            final boolean startInclusive,
            final boolean endInclusive
    ) {
        if (lowerTerm.contains(identifier)) {
            String bareLowerTerm = lowerTerm.replace(identifier, "");
            replaceIdentifier(field, lowerTerm);

            SuggesterRangeQuery rangeQuery = new SuggesterRangeQuery(field, new BytesRef(bareLowerTerm),
                    new BytesRef(upperTerm), startInclusive, endInclusive, SuggesterRangeQuery.SuggestPosition.LOWER);

            this.suggesterQuery = rangeQuery;

            return rangeQuery;
        } else if (upperTerm.contains(identifier)) {
            String bareUpperTerm = upperTerm.replace(identifier, "");
            replaceIdentifier(field, upperTerm);

            SuggesterRangeQuery rangeQuery = new SuggesterRangeQuery(field, new BytesRef(lowerTerm),
                    new BytesRef(bareUpperTerm), startInclusive, endInclusive, SuggesterRangeQuery.SuggestPosition.UPPER);

            this.suggesterQuery = rangeQuery;

            return rangeQuery;
        }

        return super.newRangeQuery(field, lowerTerm, upperTerm, startInclusive, endInclusive);
    }

}
