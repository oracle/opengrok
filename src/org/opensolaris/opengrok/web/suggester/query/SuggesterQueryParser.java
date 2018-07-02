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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web.suggester.query;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
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
import org.opensolaris.opengrok.search.CustomQueryParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

class SuggesterQueryParser extends CustomQueryParser {

    private static final Logger logger = Logger.getLogger(SuggesterQueryParser.class.getName());

    private final Set<BooleanClause> suggesterClauses = new HashSet<>();

    private String identifier;

    private SuggesterQuery suggesterQuery;

    private String queryTextWithPlaceholder;

    SuggesterQueryParser(final String field, final String identifier) {
        super(field);
        this.identifier = identifier;
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
    protected Query newTermQuery(final Term term) {
        if (term.text().contains(identifier)) {
            SuggesterPrefixQuery suggesterQuery = new SuggesterPrefixQuery(replaceIdentifier(term, identifier));
            this.suggesterQuery = suggesterQuery;
            return suggesterQuery;
        }

        return super.newTermQuery(term);
    }

    private Term replaceIdentifier(final Term term, final String identifier) {
        Term newTerm = new Term(term.field(), term.text().replace(identifier, ""));
        this.identifier = term.text();

        return newTerm;
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
            SuggesterPrefixQuery suggesterQuery = new SuggesterPrefixQuery(replaceIdentifier(prefix, identifier));
            this.suggesterQuery = suggesterQuery;
            return suggesterQuery;
        }

        return super.newPrefixQuery(prefix);
    }

    @Override
    protected Query newWildcardQuery(final Term t) {
        if (t.text().contains(identifier)) {
            String term = t.text().replace(identifier, "");
            if (term.endsWith("*")) {
                identifier = t.text();
                term = term.substring(0, term.length() - 1);
                SuggesterPrefixQuery suggesterQuery = new SuggesterPrefixQuery(new Term(t.field(), term));
                this.suggesterQuery = suggesterQuery;
                return suggesterQuery;
            } else {
                SuggesterWildcardQuery suggesterQuery = new SuggesterWildcardQuery(replaceIdentifier(t, identifier));
                identifier = t.text();
                this.suggesterQuery = suggesterQuery;
                return suggesterQuery;
            }
        }

        return super.newWildcardQuery(t);
    }

    @Override
    protected Query newFuzzyQuery(final Term term, final float minimumSimilarity, final int prefixLength) {
        if (term.text().contains(identifier)) {
            Term newTerm = replaceIdentifier(term, identifier);
            int numEdits = FuzzyQuery.floatToEdits(minimumSimilarity, newTerm.text().codePointCount(0, newTerm.text().length()));

            SuggesterFuzzyQuery suggesterQuery = new SuggesterFuzzyQuery(newTerm, numEdits, prefixLength);
            this.suggesterQuery = suggesterQuery;
            return suggesterQuery;
        }

        return super.newFuzzyQuery(term, minimumSimilarity, prefixLength);
    }

    @Override
    protected Query newRegexpQuery(final Term regexp) {
        if (regexp.text().contains(identifier)) {
            Term newTerm = replaceIdentifier(regexp, identifier);

            identifier = "/" + regexp.text() + "/";

            SuggesterRegexpQuery suggesterQuery = new SuggesterRegexpQuery(newTerm);
            this.suggesterQuery = suggesterQuery;
            return suggesterQuery;
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
            this.identifier = tokens.stream().filter(t -> t.contains(identifier)).findAny().get();
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
            final String part1,
            final String part2,
            final boolean startInclusive,
            final boolean endInclusive
    ) {
        if (part1.contains(identifier)) {
            String newPart1 = part1.replace(identifier, "");
            this.identifier = part1;

            SuggesterRangeQuery rangeQuery = new SuggesterRangeQuery(field, new BytesRef(newPart1),
                    new BytesRef(part2), startInclusive, endInclusive, SuggesterRangeQuery.SuggestPosition.LOWER);

            this.suggesterQuery = rangeQuery;

            return rangeQuery;

        } else if (part2.contains(identifier)) {
            String newPart2 = part2.replace(identifier, "");
            this.identifier = part2;

            SuggesterRangeQuery rangeQuery = new SuggesterRangeQuery(field, new BytesRef(part1),
                    new BytesRef(newPart2), startInclusive, endInclusive, SuggesterRangeQuery.SuggestPosition.UPPER);

            this.suggesterQuery = rangeQuery;

            return rangeQuery;

        }

        return super.newRangeQuery(field, part1, part2, startInclusive, endInclusive);
    }

}
