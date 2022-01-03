/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opengrok.suggest.query.customized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.opengrok.suggest.query.PhraseScorer;
import org.opengrok.suggest.query.data.BitIntsHolder;
import org.opengrok.suggest.query.data.IntsHolder;

/**
 * Modified Apache Lucene's ExactPhraseScorer (now {@link org.apache.lucene.search.ExactPhraseMatcher}) to support
 * remembering the positions where the match was found.
 */
final class CustomExactPhraseScorer extends Scorer implements PhraseScorer { // custom – special interface

    private static class PostingsAndPosition {
        private final PostingsEnum postings;
        private final int offset;
        private int freq, upTo, pos;

        PostingsAndPosition(PostingsEnum postings, int offset) {
            this.postings = postings;
            this.offset = offset;
        }
    }

    // custom begins – only necessary attributes
    private Map<Integer, IntsHolder> documentToPositionsMap = new HashMap<>();

    private int offset;

    private final DocIdSetIterator conjunction;
    private final PostingsAndPosition[] postings;
    // custom ends

    // custom – constructor parameters
    /**
     * Creates custom exact phrase scorer which remembers the positions of the found matches.
     * @param weight query weight
     * @param postings postings of the terms
     * @param offset the offset that is added to the found match position
     */
    CustomExactPhraseScorer(
            final Weight weight,
            final CustomPhraseQuery.PostingsAndFreq[] postings,
            final int offset
    ) {
        super(weight);

        this.offset = offset; // custom

        List<DocIdSetIterator> iterators = new ArrayList<>();
        List<PostingsAndPosition> postingsAndPositions = new ArrayList<>();
        for (CustomPhraseQuery.PostingsAndFreq posting : postings) {
            iterators.add(posting.postings);
            postingsAndPositions.add(new PostingsAndPosition(posting.postings, posting.position));
        }
        // custom begins – support for single term
        if (iterators.size() == 1) {
            conjunction = iterators.get(0);
        } else {
            conjunction = ConjunctionUtils.intersectIterators(iterators);
        }
        // custom ends
        assert TwoPhaseIterator.unwrap(conjunction) == null;
        this.postings = postingsAndPositions.toArray(new PostingsAndPosition[postingsAndPositions.size()]);
    }

    @Override
    public TwoPhaseIterator twoPhaseIterator() {
        return new TwoPhaseIterator(conjunction) {
            @Override
            public boolean matches() throws IOException {
                // custom – interrupt handler
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Interrupted while scoring documents");
                }
                return phraseFreq() > 0; // custom – only necessary part left
            }

            @Override
            public float matchCost() {
                return 0; // custom – default value
            }
        };
    }

    @Override
    public float getMaxScore(int i) throws IOException {
        return 0;
    }

    @Override
    public DocIdSetIterator iterator() {
        return TwoPhaseIterator.asDocIdSetIterator(twoPhaseIterator());
    }

    @Override
    public String toString() {
        return "CustomExactPhraseScorer(" + weight + ")"; // custom – renamed class
    }

    @Override
    public int docID() {
        return conjunction.docID();
    }

    @Override
    public float score() {
        return 1; // custom – default value
    }

    /** Advance the given pos enum to the first doc on or after {@code target}.
     *  Return {@code false} if the enum was exhausted before reaching
     *  {@code target} and {@code true} otherwise. */
    private static boolean advancePosition(PostingsAndPosition posting, int target) throws IOException {
        while (posting.pos < target) {
            if (posting.upTo == posting.freq) {
                return false;
            } else {
                posting.pos = posting.postings.nextPosition();
                posting.upTo += 1;
            }
        }
        return true;
    }

    private int phraseFreq() throws IOException {
        // reset state
        final PostingsAndPosition[] postings = this.postings;
        for (PostingsAndPosition posting : postings) {
            posting.freq = posting.postings.freq();
            posting.pos = posting.postings.nextPosition();
            posting.upTo = 1;
        }

        int freq = 0;
        final PostingsAndPosition lead = postings[0];

        BitIntsHolder positions = null; // custom – store positions

        advanceHead:
        while (true) {
            final int phrasePos = lead.pos - lead.offset;
            for (int j = 1; j < postings.length; ++j) {
                final PostingsAndPosition posting = postings[j];
                final int expectedPos = phrasePos + posting.offset;

                // advance up to the same position as the lead
                if (!advancePosition(posting, expectedPos)) {
                    break advanceHead;
                }

                if (posting.pos != expectedPos) { // we advanced too far
                    if (advancePosition(lead, posting.pos - posting.offset + lead.offset)) {
                        continue advanceHead;
                    } else {
                        break advanceHead;
                    }
                }
            }

            freq += 1;
            // custom begins – found a match
            if (positions == null) {
                positions = new BitIntsHolder();
            }
            positions.set(phrasePos + offset);
            // custom ends

            if (lead.upTo == lead.freq) {
                break;
            }
            lead.pos = lead.postings.nextPosition();
            lead.upTo += 1;
        }

        // custom begin – if some positions were found then store them
        if (positions != null) {
            documentToPositionsMap.put(docID(), positions);
        }
        // custom ends

        return freq;
    }

    // custom begins – special interface implementation
    /** {@inheritDoc} */
    @Override
    public IntsHolder getPositions(final int docId) {
        return documentToPositionsMap.get(docId);
    }
    // custom ends

}
