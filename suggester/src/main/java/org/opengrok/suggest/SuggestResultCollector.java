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
 * Copyright (c) 2023, Oracle and/or its affiliates.
 * Portions Copyright (c) 2023, Gino Augustine <gino.augustine@oracle.com>.
 */
package org.opengrok.suggest;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.CollectionTerminatedException;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.opengrok.suggest.query.PhraseScorer;
import org.opengrok.suggest.query.data.BitIntsHolder;

import java.io.IOException;
import java.util.Collection;

/**
 * Collects Suggester query results.
 * @author Gino Augustine
 */
class SuggestResultCollector implements Collector {
    private final LeafReaderContext leafReaderContext;
    private final ComplexQueryData data;
    private final BitIntsHolder documentIds;

    SuggestResultCollector(LeafReaderContext leafReaderContext, ComplexQueryData data,
                                  BitIntsHolder documentIds) {
        this.leafReaderContext = leafReaderContext;
        this.data = data;
        this.documentIds = documentIds;
    }

    /**
     * Create a new {@link LeafCollector collector} to collect the given context.
     *
     * @param context next atomic reader context
     */
    @Override
    public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
        return new SuggesterLeafCollector(context);
    }

    /**
     * Creates a {@link CollectorManager} that can concurrently collect matching docs in a {@link
     * BitIntsHolder}.
     */
    public static CollectorManager<SuggestResultCollector, BitIntsHolder> createManager(LeafReaderContext leafReaderContext, ComplexQueryData data,
                                                                                        BitIntsHolder documentIds) {
        return new CollectorManager<>() {
            @Override
            public SuggestResultCollector newCollector() {
                BitIntsHolder docIds = new BitIntsHolder();
                return new SuggestResultCollector(leafReaderContext, data, docIds);
            }

            @Override
            public BitIntsHolder reduce(Collection<SuggestResultCollector> collectors) {
                for (SuggestResultCollector collector : collectors) {
                    documentIds.or(collector.documentIds);
                }
                return documentIds;
            }
        };
    }

    /**
     * Indicates what features are required from the scorer.
     */
    @Override
    public ScoreMode scoreMode() {
        return ScoreMode.COMPLETE_NO_SCORES;
    }

    private final class SuggesterLeafCollector implements LeafCollector {
        private final LeafReaderContext context;
        private final int docBase;

        private SuggesterLeafCollector(LeafReaderContext context) {
            this.context = context;
            docBase = this.context.docBase;
        }

        /**
         * Called before successive calls to {@link #collect(int)}. Implementations that need the score of
         * the current document (passed-in to {@link #collect(int)}), should save the passed-in Scorer and
         * call scorer.score() when needed.
         *
         * @param scorer scorer
         */
        @Override
        public void setScorer(Scorable scorer) throws IOException {
            if (leafReaderContext == context) {
                if (scorer instanceof PhraseScorer) {
                    data.scorer = (PhraseScorer) scorer;
                } else {
                    try {
                        // it is mentioned in the documentation that #getChildren should not be called
                        // in #setScorer but no better way was found
                        for (var childScorer : scorer.getChildren()) {
                            if (childScorer.child instanceof PhraseScorer) {
                                data.scorer = (PhraseScorer) childScorer.child;
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }

        /**
         * Called once for every document matching a query, with the unbased document number.
         *
         * <p>Note: The collection of the current segment can be terminated by throwing a {@link
         * CollectionTerminatedException}. In this case, the last docs of the current {@link
         * LeafReaderContext} will be skipped and {@link IndexSearcher} will
         * swallow the exception and continue collection with the next leaf.
         *
         * <p>Note: This is called in an inner search loop. For good search performance, implementations
         * of this method should not call {@link StoredFields#document} on every hit. Doing so can slow
         * searches by an order of magnitude or more.
         *
         * @param doc documentId
         */
        @Override
        public void collect(int doc) throws IOException {
            if (leafReaderContext == context) {
                documentIds.set(docBase + doc);
            }
        }
    }
}
