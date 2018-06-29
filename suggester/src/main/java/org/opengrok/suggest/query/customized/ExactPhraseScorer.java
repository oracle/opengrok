//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.opengrok.suggest.query.customized;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.search.ConjunctionDISI;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity.SimScorer;
import org.opengrok.suggest.query.PhraseScorer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExactPhraseScorer extends Scorer implements PhraseScorer {

    public int suggestOffset = 0;

    private static final Logger logger = Logger.getLogger(ExactPhraseScorer.class.getName());

    private final DocIdSetIterator conjunction;
    private final ExactPhraseScorer.PostingsAndPosition[] postings;
    private int freq;
    private final SimScorer docScorer;
    private final boolean needsScores;
    private float matchCost;

    public Map<Integer, Set<Integer>> map = new HashMap<>();

    ExactPhraseScorer(Weight weight, MyPhraseQuery.PostingsAndFreq[] postings, SimScorer docScorer, boolean needsScores, float matchCost) throws IOException {
        super(weight);
        this.docScorer = docScorer;
        this.needsScores = needsScores;
        List<DocIdSetIterator> iterators = new ArrayList();
        List<PostingsAndPosition> postingsAndPositions = new ArrayList();
        MyPhraseQuery.PostingsAndFreq[] var8 = postings;
        int var9 = postings.length;

        for(int var10 = 0; var10 < var9; ++var10) {
            MyPhraseQuery.PostingsAndFreq posting = var8[var10];
            iterators.add(posting.postings);
            postingsAndPositions.add(new ExactPhraseScorer.PostingsAndPosition(posting.postings, posting.position));
        }

        if (iterators.size() > 1) {
            this.conjunction = ConjunctionDISI.intersectIterators(iterators);
        } else {
            this.conjunction = iterators.get(0);
        }

        assert TwoPhaseIterator.unwrap(this.conjunction) == null;

        this.postings = (ExactPhraseScorer.PostingsAndPosition[])postingsAndPositions.toArray(new ExactPhraseScorer.PostingsAndPosition[postingsAndPositions.size()]);
        this.matchCost = matchCost;
    }

    public TwoPhaseIterator twoPhaseIterator() {
        return new TwoPhaseIterator(this.conjunction) {
            public boolean matches() throws IOException {
                return ExactPhraseScorer.this.phraseFreq() > 0;
            }

            public float matchCost() {
                return ExactPhraseScorer.this.matchCost;
            }
        };
    }

    public DocIdSetIterator iterator() {
        return TwoPhaseIterator.asDocIdSetIterator(this.twoPhaseIterator());
    }

    public String toString() {
        return "ExactPhraseScorer(" + this.weight + ")";
    }

    final int freq() {
        return this.freq;
    }

    public int docID() {
        return this.conjunction.docID();
    }

    public float score() throws IOException {
        return this.docScorer.score(this.docID(), (float)this.freq);
    }

    private static boolean advancePosition(ExactPhraseScorer.PostingsAndPosition posting, int target) throws IOException {
        while(posting.pos < target) {
            if (posting.upTo == posting.freq) {
                return false;
            }

            try {
                posting.pos = posting.postings.nextPosition();
            } catch (Exception e) {
                logger.log(Level.WARNING, "upTo: " + posting.upTo + ", freq: " + posting.freq, e);
            }
            posting.upTo = posting.upTo + 1;
        }

        return true;
    }

    private int phraseFreq() throws IOException {
        ExactPhraseScorer.PostingsAndPosition[] postings = this.postings;
        ExactPhraseScorer.PostingsAndPosition[] var2 = postings;
        int var3 = postings.length;

        int phrasePos;
        for(phrasePos = 0; phrasePos < var3; ++phrasePos) {
            ExactPhraseScorer.PostingsAndPosition posting = var2[phrasePos];
            posting.freq = posting.postings.freq();
            try {
                posting.pos = posting.postings.nextPosition();
            } catch (Exception e) {
                logger.log(Level.WARNING, "upTo: " + posting.upTo + ", freq: " + posting.freq, e);
            }
            posting.upTo = 1;
        }

        int freq = 0;
        ExactPhraseScorer.PostingsAndPosition lead = postings[0];

        Set<Integer> positions = new HashSet<>();
        map.put(docID(), positions);

        label36:
        while(true) {
            phrasePos = lead.pos - lead.offset;

            for(int j = 1; j < postings.length; ++j) {
                ExactPhraseScorer.PostingsAndPosition posting = postings[j];
                int expectedPos = phrasePos + posting.offset;
                if (!advancePosition(posting, expectedPos)) {
                    return this.freq = freq;
                }

                if (posting.pos != expectedPos) {
                    if (advancePosition(lead, posting.pos - posting.offset + lead.offset)) {
                        continue label36;
                    }

                    return this.freq = freq;
                }
            }

            positions.add(phrasePos + suggestOffset);

            ++freq;
            //if (!this.needsScores || lead.upTo == lead.freq) {
            if (lead.upTo == lead.freq) {
                return this.freq = freq;
            }

            lead.pos = lead.postings.nextPosition();
            lead.upTo = lead.upTo + 1;
        }
    }

    @Override
    public Map<Integer, Set<Integer>> getMap() {
        return map;
    }

    private static class PostingsAndPosition {
        private final PostingsEnum postings;
        private final int offset;
        private int freq;
        private int upTo;
        private int pos;

        public PostingsAndPosition(PostingsEnum postings, int offset) {
            this.postings = postings;
            this.offset = offset;
        }
    }
}
