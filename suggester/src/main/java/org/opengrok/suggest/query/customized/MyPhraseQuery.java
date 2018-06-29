//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.opengrok.suggest.query.customized;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.Similarity.SimScorer;
import org.apache.lucene.search.similarities.Similarity.SimWeight;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MyPhraseQuery extends Query {

    public int offset;

    private final int slop;
    private final String field;
    private final Term[] terms;
    private final int[] positions;
    private static final int TERM_POSNS_SEEK_OPS_PER_DOC = 128;
    private static final int TERM_OPS_PER_POS = 7;

    public MyPhraseQuery(int slop, Term[] terms, int[] positions) {
        if (terms.length != positions.length) {
            throw new IllegalArgumentException("Must have as many terms as positions");
        } else if (slop < 0) {
            throw new IllegalArgumentException("Slop must be >= 0, got " + slop);
        } else {
            int i;
            for(i = 1; i < terms.length; ++i) {
                if (!terms[i - 1].field().equals(terms[i].field())) {
                    throw new IllegalArgumentException("All terms should have the same field");
                }
            }

            int[] var8 = positions;
            int var5 = positions.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                int position = var8[var6];
                if (position < 0) {
                    throw new IllegalArgumentException("Positions must be >= 0, got " + position);
                }
            }

            for(i = 1; i < positions.length; ++i) {
                if (positions[i] < positions[i - 1]) {
                    throw new IllegalArgumentException("Positions should not go backwards, got " + positions[i - 1] + " before " + positions[i]);
                }
            }

            this.slop = slop;
            this.terms = terms;
            this.positions = positions;
            this.field = terms.length == 0 ? null : terms[0].field();
        }
    }

    private static int[] incrementalPositions(int length) {
        int[] positions = new int[length];

        for(int i = 0; i < length; positions[i] = i++) {
            ;
        }

        return positions;
    }

    private static Term[] toTerms(String field, String... termStrings) {
        Term[] terms = new Term[termStrings.length];

        for(int i = 0; i < terms.length; ++i) {
            terms[i] = new Term(field, termStrings[i]);
        }

        return terms;
    }

    private static Term[] toTerms(String field, BytesRef... termBytes) {
        Term[] terms = new Term[termBytes.length];

        for(int i = 0; i < terms.length; ++i) {
            terms[i] = new Term(field, termBytes[i]);
        }

        return terms;
    }

    public MyPhraseQuery(int slop, String field, String... terms) {
        this(slop, toTerms(field, terms), incrementalPositions(terms.length));
    }

    public MyPhraseQuery(String field, String... terms) {
        this(0, (String)field, (String[])terms);
    }

    public MyPhraseQuery(int slop, String field, BytesRef... terms) {
        this(slop, toTerms(field, terms), incrementalPositions(terms.length));
    }

    public MyPhraseQuery(String field, BytesRef... terms) {
        this(0, (String)field, (BytesRef[])terms);
    }

    public int getSlop() {
        return this.slop;
    }

    public Term[] getTerms() {
        return this.terms;
    }

    public int[] getPositions() {
        return this.positions;
    }

    public Query rewrite(IndexReader reader) {
        if (this.terms.length == 0) {
            return new MatchAllDocsQuery();
            //return new MatchNoDocsQuery("empty MyPhraseQuery");
        } /*else if (this.terms.length == 1) {

            return new TermQuery(this.terms[0]);
        }*/

        return this;
        /*else if (this.positions[0] == 0) {
            return super.rewrite(reader);
        } else {
            int[] newPositions = new int[this.positions.length];

            for(int i = 0; i < this.positions.length; ++i) {
                newPositions[i] = this.positions[i] - this.positions[0];
            }

            return new MyPhraseQuery(this.slop, this.terms, newPositions);
        }*/
    }

    static float termPositionsCost(TermsEnum termsEnum) throws IOException {
        int docFreq = termsEnum.docFreq();

        assert docFreq > 0;

        long totalTermFreq = termsEnum.totalTermFreq();
        float expOccurrencesInMatchingDoc = totalTermFreq < (long)docFreq ? 1.0F : (float)totalTermFreq / (float)docFreq;
        return 128.0F + expOccurrencesInMatchingDoc * 7.0F;
    }

    public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
        return new MyPhraseQuery.PhraseWeight(searcher, needsScores, boost);
    }

    public String toString(String f) {
        StringBuilder buffer = new StringBuilder();
        if (this.field != null && !this.field.equals(f)) {
            buffer.append(this.field);
            buffer.append(":");
        }

        buffer.append("\"");
        int maxPosition;
        if (this.positions.length == 0) {
            maxPosition = -1;
        } else {
            maxPosition = this.positions[this.positions.length - 1];
        }

        String[] pieces = new String[maxPosition + 1];

        int i;
        for(i = 0; i < this.terms.length; ++i) {
            int pos = this.positions[i];
            String s = pieces[pos];
            if (s == null) {
                s = this.terms[i].text();
            } else {
                s = s + "|" + this.terms[i].text();
            }

            pieces[pos] = s;
        }

        for(i = 0; i < pieces.length; ++i) {
            if (i > 0) {
                buffer.append(' ');
            }

            String s = pieces[i];
            if (s == null) {
                buffer.append('?');
            } else {
                buffer.append(s);
            }
        }

        buffer.append("\"");
        if (this.slop != 0) {
            buffer.append("~");
            buffer.append(this.slop);
        }

        return buffer.toString();
    }

    public boolean equals(Object other) {
        return this.sameClassAs(other) && this.equalsTo((MyPhraseQuery)this.getClass().cast(other));
    }

    private boolean equalsTo(MyPhraseQuery other) {
        return this.slop == other.slop && Arrays.equals(this.terms, other.terms) && Arrays.equals(this.positions, other.positions);
    }

    public int hashCode() {
        int h = this.classHash();
        h = 31 * h + this.slop;
        h = 31 * h + Arrays.hashCode(this.terms);
        h = 31 * h + Arrays.hashCode(this.positions);
        return h;
    }

    private class PhraseWeight extends Weight {
        private final Similarity similarity;
        private final SimWeight stats;
        private final boolean needsScores;
        private transient TermContext[] states;

        public PhraseWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
            super(MyPhraseQuery.this);
            int[] positions = MyPhraseQuery.this.getPositions();
            /*if (positions.length < 2) {
                throw new IllegalStateException("PhraseWeight does not support less than 2 terms, call rewrite first");
            } else*/ if (positions[0] != 0) {
                throw new IllegalStateException("PhraseWeight requires that the first position is 0, call rewrite first");
            } else {
                this.needsScores = needsScores;
                this.similarity = searcher.getSimilarity(needsScores);
                IndexReaderContext context = searcher.getTopReaderContext();
                this.states = new TermContext[MyPhraseQuery.this.terms.length];
                TermStatistics[] termStats = new TermStatistics[MyPhraseQuery.this.terms.length];

                for(int i = 0; i < MyPhraseQuery.this.terms.length; ++i) {
                    Term term = MyPhraseQuery.this.terms[i];
                    this.states[i] = TermContext.build(context, term);
                    termStats[i] = searcher.termStatistics(term, this.states[i]);
                }

                this.stats = this.similarity.computeWeight(boost, searcher.collectionStatistics(MyPhraseQuery.this.field), termStats);
            }
        }

        public void extractTerms(Set<Term> queryTerms) {
            Collections.addAll(queryTerms, MyPhraseQuery.this.terms);
        }

        public String toString() {
            return "weight(" + MyPhraseQuery.this + ")";
        }

        public Scorer scorer(LeafReaderContext context) throws IOException {
            assert MyPhraseQuery.this.terms.length > 0;

            LeafReader reader = context.reader();
            MyPhraseQuery.PostingsAndFreq[] postingsFreqs = new MyPhraseQuery.PostingsAndFreq[MyPhraseQuery.this.terms.length];
            Terms fieldTerms = reader.terms(MyPhraseQuery.this.field);
            if (fieldTerms == null) {
                return null;
            } else if (!fieldTerms.hasPositions()) {
                throw new IllegalStateException("field \"" + MyPhraseQuery.this.field + "\" was indexed without position data; cannot run MyPhraseQuery (phrase=" + this.getQuery() + ")");
            } else {
                TermsEnum te = fieldTerms.iterator();
                float totalMatchCost = 0.0F;

                for(int i = 0; i < MyPhraseQuery.this.terms.length; ++i) {
                    Term t = MyPhraseQuery.this.terms[i];
                    TermState state = this.states[i].get(context.ord);
                    if (state == null) {
                        assert this.termNotInReader(reader, t) : "no termstate found but term exists in reader";

                        return null;
                    }

                    te.seekExact(t.bytes(), state);
                    PostingsEnum postingsEnum = te.postings((PostingsEnum)null, 24);
                    postingsFreqs[i] = new MyPhraseQuery.PostingsAndFreq(postingsEnum, MyPhraseQuery.this.positions[i], new Term[]{t});
                    totalMatchCost += MyPhraseQuery.termPositionsCost(te);
                }

                if (MyPhraseQuery.this.slop == 0) {
                    ArrayUtil.timSort(postingsFreqs);
                    ExactPhraseScorer scorer = new ExactPhraseScorer(this, postingsFreqs,
                            this.similarity.simScorer(this.stats, context), this.needsScores, totalMatchCost);

                    scorer.suggestOffset = offset;

                    return scorer;

                } else {
                    SloppyPhraseScorer sloppyPhraseScorer = new SloppyPhraseScorer(this, postingsFreqs, MyPhraseQuery.this.slop, this.similarity.simScorer(this.stats, context), this.needsScores, totalMatchCost);

                    sloppyPhraseScorer.suggestOffset = offset;

                    return sloppyPhraseScorer;
                }

            }
        }

        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }

        private boolean termNotInReader(LeafReader reader, Term term) throws IOException {
            return reader.docFreq(term) == 0;
        }

        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = this.scorer(context);
            if (scorer != null) {
                int newDoc = scorer.iterator().advance(doc);
                if (newDoc == doc) {
                    float freq = ((ExactPhraseScorer) scorer).freq(); //MyPhraseQuery.this.slop == 0 ? (float)((ExactPhraseScorer)scorer).freq() : ((SloppyPhraseScorer)scorer).sloppyFreq();
                    SimScorer docScorer = this.similarity.simScorer(this.stats, context);
                    Explanation freqExplanation = Explanation.match(freq, "phraseFreq=" + freq, new Explanation[0]);
                    Explanation scoreExplanation = docScorer.explain(doc, freqExplanation);
                    return Explanation.match(scoreExplanation.getValue(), "weight(" + this.getQuery() + " in " + doc + ") [" + this.similarity.getClass().getSimpleName() + "], result of:", new Explanation[]{scoreExplanation});
                }
            }

            return Explanation.noMatch("no matching term", new Explanation[0]);
        }
    }

    static class PostingsAndFreq implements Comparable<PostingsAndFreq> {
        final PostingsEnum postings;
        final int position;
        final Term[] terms;
        final int nTerms;

        public PostingsAndFreq(PostingsEnum postings, int position, Term... terms) {
            this.postings = postings;
            this.position = position;
            this.nTerms = terms == null ? 0 : terms.length;
            if (this.nTerms > 0) {
                if (terms.length == 1) {
                    this.terms = terms;
                } else {
                    Term[] terms2 = new Term[terms.length];
                    System.arraycopy(terms, 0, terms2, 0, terms.length);
                    Arrays.sort(terms2);
                    this.terms = terms2;
                }
            } else {
                this.terms = null;
            }

        }

        public int compareTo(MyPhraseQuery.PostingsAndFreq other) {
            if (this.position != other.position) {
                return this.position - other.position;
            } else if (this.nTerms != other.nTerms) {
                return this.nTerms - other.nTerms;
            } else if (this.nTerms == 0) {
                return 0;
            } else {
                for(int i = 0; i < this.terms.length; ++i) {
                    int res = this.terms[i].compareTo(other.terms[i]);
                    if (res != 0) {
                        return res;
                    }
                }

                return 0;
            }
        }

        public int hashCode() {
            int result = 1;
            result = 31 * result + this.position;

            for(int i = 0; i < this.nTerms; ++i) {
                result = 31 * result + this.terms[i].hashCode();
            }

            return result;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (this.getClass() != obj.getClass()) {
                return false;
            } else {
                MyPhraseQuery.PostingsAndFreq other = (MyPhraseQuery.PostingsAndFreq)obj;
                if (this.position != other.position) {
                    return false;
                } else if (this.terms == null) {
                    return other.terms == null;
                } else {
                    return Arrays.equals(this.terms, other.terms);
                }
            }
        }
    }

    public static class Builder {
        private int slop = 0;
        private final List<Term> terms = new ArrayList();
        private final List<Integer> positions = new ArrayList();

        public Builder() {
        }

        public MyPhraseQuery.Builder setSlop(int slop) {
            this.slop = slop;
            return this;
        }

        public MyPhraseQuery.Builder add(Term term) {
            return this.add(term, this.positions.isEmpty() ? 0 : 1 + (Integer)this.positions.get(this.positions.size() - 1));
        }

        public MyPhraseQuery.Builder add(Term term, int position) {
            if (position < 0) {
                throw new IllegalArgumentException("Positions must be >= 0, got " + position);
            } else {
                if (!this.positions.isEmpty()) {
                    int lastPosition = (Integer)this.positions.get(this.positions.size() - 1);
                    if (position < lastPosition) {
                        throw new IllegalArgumentException("Positions must be added in order, got " + position + " after " + lastPosition);
                    }
                }

                if (!this.terms.isEmpty() && !term.field().equals(((Term)this.terms.get(0)).field())) {
                    throw new IllegalArgumentException("All terms must be on the same field, got " + term.field() + " and " + ((Term)this.terms.get(0)).field());
                } else {
                    this.terms.add(term);
                    this.positions.add(position);
                    return this;
                }
            }
        }

        public MyPhraseQuery build() {
            Term[] terms = (Term[])this.terms.toArray(new Term[this.terms.size()]);
            int[] positions = new int[this.positions.size()];

            for(int i = 0; i < positions.length; ++i) {
                positions[i] = (Integer)this.positions.get(i);
            }

            return new MyPhraseQuery(this.slop, terms, positions);
        }
    }
}
