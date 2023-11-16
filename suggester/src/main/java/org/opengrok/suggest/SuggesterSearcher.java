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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.suggest;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.opengrok.suggest.popular.PopularityCounter;
import org.opengrok.suggest.query.SuggesterRangeQuery;
import org.opengrok.suggest.query.data.BitIntsHolder;
import org.opengrok.suggest.query.data.IntsHolder;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opengrok.suggest.query.customized.CustomPhraseQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Variation of {@link IndexSearcher} but instead of returning the relevant documents can return also suggestions.
 */
class SuggesterSearcher extends IndexSearcher {

    public static final int TERM_ALREADY_SEARCHED_MULTIPLIER = 100;

    private static final Logger logger = Logger.getLogger(SuggesterSearcher.class.getName());

    private final int resultSize;

    private boolean interrupted;

    private final int numDocs;

    private final IndexSearcher is;

    /**
     * @param reader reader of the index for which to provide suggestions
     * @param resultSize size of the results
     */
    SuggesterSearcher(final IndexReader reader, final int resultSize) {
        super(reader);
        is = new IndexSearcher(reader);
        numDocs = reader.numDocs();
        this.resultSize = resultSize;
    }

    /**
     * Returns the suggestions for generic {@link SuggesterQuery} (almost all except lone
     * {@link org.opengrok.suggest.query.SuggesterPrefixQuery} for which see {@link SuggesterProjectData}).
     * @param query query on which the suggestions depend
     * @param project name of the project
     * @param suggesterQuery query for the suggestions
     * @param popularityCounter data structure which contains the number of times the terms were searched for. It is
     * used to provide the most popular completion functionality.
     * @return suggestions
     */
    public List<LookupResultItem> suggest(
            final Query query,
            final String project,
            final SuggesterQuery suggesterQuery,
            final PopularityCounter popularityCounter
    ) {
        List<LookupResultItem> results = new ArrayList<>(resultSize * leafContexts.size());

        Query rewrittenQuery = null;

        try {
            if (query != null) {
                rewrittenQuery = query.rewrite(is);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not rewrite query", e);
            return results;
        }

        for (LeafReaderContext context : this.leafContexts) {
            if (interrupted) {
                break;
            }
            try {
                results.addAll(suggest(rewrittenQuery, context, project, suggesterQuery, popularityCounter));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Cannot perform suggester search", e);
            }
        }

        if (results.size() > resultSize) {
            return SuggesterUtils.combineResults(results, resultSize);
        }

        return results;
    }

    private List<LookupResultItem> suggest(
            final Query query,
            final LeafReaderContext leafReaderContext,
            final String project,
            final SuggesterQuery suggesterQuery,
            final PopularityCounter searchCounts
    ) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            interrupted = true;
            return Collections.emptyList();
        }

        Set<BytesRef> tokensAlreadyIncluded = null;
        if (shouldLeaveOutSameTerms(query, suggesterQuery)) {
            tokensAlreadyIncluded = SuggesterUtils.intoTermsExceptPhraseQuery(query).stream()
                    .filter(t -> t.field().equals(suggesterQuery.getField()))
                    .map(Term::bytes)
                    .collect(Collectors.toSet());
        }

        boolean needsDocumentIds = Optional.ofNullable(query)
                                    .filter(query1 -> !(query1 instanceof MatchAllDocsQuery))
                                    .isPresent();

        ComplexQueryData complexQueryData = null;
        if (needsDocumentIds) {
            complexQueryData = getComplexQueryData(query, leafReaderContext);
        }
        if (interrupted) {
            return Collections.emptyList();
        }

        Terms terms = leafReaderContext.reader().terms(suggesterQuery.getField());

        TermsEnum termsEnum = suggesterQuery.getTermsEnumForSuggestions(terms);

        LookupPriorityQueue queue = new LookupPriorityQueue(resultSize);

        boolean needPositionsAndFrequencies = needPositionsAndFrequencies(query);

        PostingsEnum postingsEnum = null;

        BytesRef term = termsEnum.next();
        while (term != null) {
            if (Thread.currentThread().isInterrupted()) {
                interrupted = true;
                break;
            }
            postingsEnum = derivePostingsEnum(postingsEnum, termsEnum, needPositionsAndFrequencies);

            int score = 0;
            if (!needsDocumentIds) {
                score = normalizeDocumentFrequency(termsEnum.docFreq(), numDocs);
            } else if (needPositionsAndFrequencies) {
                score = getPhraseScore(complexQueryData, leafReaderContext.docBase, postingsEnum);
            } else if (complexQueryData != null) {
                score = getDocumentFrequency(complexQueryData.documentIds, leafReaderContext.docBase, postingsEnum);
            }

            if (shouldAddScoreForTerm(score, term, tokensAlreadyIncluded)) {
                score += searchCounts.get(term) * TERM_ALREADY_SEARCHED_MULTIPLIER;
                insertScoreToQueue(queue, score, term, project);
            }

            term = termsEnum.next();
        }

        return queue.getResult();
    }

    private PostingsEnum derivePostingsEnum(PostingsEnum postingsEnum,
                                           TermsEnum termsEnum,
                                           boolean needPositionsAndFrequencies) throws IOException {
        if (needPositionsAndFrequencies) {
            return termsEnum.postings(postingsEnum, PostingsEnum.POSITIONS | PostingsEnum.FREQS);
        } else {
            return termsEnum.postings(postingsEnum, PostingsEnum.NONE);
        }
    }

    private boolean shouldAddScoreForTerm(int score, BytesRef term,
                                          Set<BytesRef> tokensAlreadyIncluded) {
        return score > 0 && (Objects.isNull(tokensAlreadyIncluded) || !tokensAlreadyIncluded.contains(term));

    }
    private void insertScoreToQueue(LookupPriorityQueue queue, int score,
                                    BytesRef term, String project) {
        if (queue.canInsert(score)) {
            queue.insertWithOverflow(new LookupResultItem(term.utf8ToString(), project, score));
        }

    }

    private boolean shouldLeaveOutSameTerms(final Query query, final SuggesterQuery suggesterQuery) {
        if (query instanceof CustomPhraseQuery) {
            return false;
        }
        return !(suggesterQuery instanceof SuggesterRangeQuery);
    }

    private ComplexQueryData getComplexQueryData(final Query query, final LeafReaderContext leafReaderContext) {
        ComplexQueryData data = new ComplexQueryData();
        if (query == null || query instanceof SuggesterQuery) {
            data.documentIds = new BitIntsHolder(0);
            return data;
        }

        BitIntsHolder documentIds = new BitIntsHolder();
        try {
            search(query, new SuggestResultCollector(leafReaderContext, data, documentIds));
        } catch (IOException e) {
            if (Thread.currentThread().isInterrupted()) {
                interrupted = true;
                return null;
            } else {
                logger.log(Level.WARNING, e, () -> "Could not get document ids for " + query);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () -> "Could not get document ids for " + query);
        }

        data.documentIds = documentIds;
        return data;
    }

    private int getPhraseScore(final ComplexQueryData data, final int docBase, final PostingsEnum postingsEnum)
            throws IOException {

        int weight = 0;
        while (postingsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            int docId = postingsEnum.docID();
            if (data.documentIds.has(docBase + docId)) {
                IntsHolder positions = data.scorer.getPositions(docId);
                if (positions == null) {
                    continue;
                }

                int freq = postingsEnum.freq();
                for (int i = 0; i < freq; i++) {
                    int pos = postingsEnum.nextPosition();

                    if (positions.has(pos)) {
                        weight++;
                    }
                }
            }
        }

        return weight;
    }

    private int getDocumentFrequency(final IntsHolder documentIds, final int docBase, final PostingsEnum postingsEnum)
            throws IOException {

        int weight = 0;
        while (postingsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            if (documentIds.has(docBase + postingsEnum.docID())) {
                weight++;
            }
        }
        return normalizeDocumentFrequency(weight, documentIds.numberOfElements());
    }

    private boolean needPositionsAndFrequencies(final Query query) {
        if (query instanceof CustomPhraseQuery) {
            return true;
        }

        if (query instanceof BooleanQuery) {
            for (BooleanClause bc : ((BooleanQuery) query).clauses()) {
                if (needPositionsAndFrequencies(bc.getQuery())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int normalizeDocumentFrequency(final int count, final int documents) {
        return (int) (((double) count / documents) * SuggesterUtils.NORMALIZED_DOCUMENT_FREQUENCY_MULTIPLIER);
    }

}
