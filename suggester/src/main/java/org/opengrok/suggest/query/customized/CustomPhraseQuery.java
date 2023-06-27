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

import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * Modified Apache Lucene's {@link PhraseQuery} to allow to use {@link CustomExactPhraseScorer} and
 * {@link CustomSloppyPhraseScorer}.
 */
public class CustomPhraseQuery extends Query {

    private int offset;

    private final int slop;
    private final String field;
    private final Term[] terms;
    private final int[] positions;

    public CustomPhraseQuery(int slop, Term[] terms, int[] positions) {
        if (terms.length != positions.length) {
            throw new IllegalArgumentException("Must have as many terms as positions");
        }
        if (slop < 0) {
            throw new IllegalArgumentException("Slop must be >= 0, got " + slop);
        }
        for (int i = 1; i < terms.length; ++i) {
            if (!terms[i - 1].field().equals(terms[i].field())) {
                throw new IllegalArgumentException("All terms should have the same field");
            }
        }
        for (int position : positions) {
            if (position < 0) {
                throw new IllegalArgumentException("Positions must be >= 0, got " + position);
            }
        }
        for (int i = 1; i < positions.length; ++i) {
            if (positions[i] < positions[i - 1]) {
                throw new IllegalArgumentException("Positions should not go backwards, got "
                        + positions[i-1] + " before " + positions[i]);
            }
        }
        this.slop = slop;
        this.terms = terms;
        this.positions = positions;
        this.field = terms.length == 0 ? null : terms[0].field();
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    private static int[] incrementalPositions(int length) {
        int[] positions = new int[length];
        for (int i = 0; i < length; ++i) {
            positions[i] = i;
        }
        return positions;
    }

    private static Term[] toTerms(String field, String... termStrings) {
        Term[] terms = new Term[termStrings.length];
        for (int i = 0; i < terms.length; ++i) {
            terms[i] = new Term(field, termStrings[i]);
        }
        return terms;
    }

    private static Term[] toTerms(String field, BytesRef... termBytes) {
        Term[] terms = new Term[termBytes.length];
        for (int i = 0; i < terms.length; ++i) {
            terms[i] = new Term(field, termBytes[i]);
        }
        return terms;
    }

    /**
     * Create a phrase query which will match documents that contain the given
     * list of terms at consecutive positions in {@code field}, and at a
     * maximum edit distance of {@code slop}. For more complicated use-cases,
     * use {@link PhraseQuery.Builder}.
     * @see #getSlop()
     */
    public CustomPhraseQuery(int slop, String field, String... terms) {
        this(slop, toTerms(field, terms), incrementalPositions(terms.length));
    }

    /**
     * Create a phrase query which will match documents that contain the given
     * list of terms at consecutive positions in {@code field}.
     */
    public CustomPhraseQuery(String field, String... terms) {
        this(0, field, terms);
    }

    /**
     * Create a phrase query which will match documents that contain the given
     * list of terms at consecutive positions in {@code field}, and at a
     * maximum edit distance of {@code slop}. For more complicated use-cases,
     * use {@link PhraseQuery.Builder}.
     * @see #getSlop()
     */
    public CustomPhraseQuery(int slop, String field, BytesRef... terms) {
        this(slop, toTerms(field, terms), incrementalPositions(terms.length));
    }

    /**
     * Create a phrase query which will match documents that contain the given
     * list of terms at consecutive positions in {@code field}.
     */
    public CustomPhraseQuery(String field, BytesRef... terms) {
        this(0, field, terms);
    }

    public int getSlop() {
        return slop;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CustomPhraseQuery that = (CustomPhraseQuery) o;
        return offset == that.offset &&
                slop == that.slop &&
                Objects.equals(field, that.field) &&
                Arrays.equals(terms, that.terms) &&
                Arrays.equals(positions, that.positions);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(offset, slop, field);
        result = 31 * result + Arrays.hashCode(terms);
        result = 31 * result + Arrays.hashCode(positions);
        return result;
    }

    /** Prints a user-readable version of this query. */
    @Override
    public String toString(String f) {
        StringBuilder buffer = new StringBuilder();
        if (field != null && !field.equals(f)) {
            buffer.append(field);
            buffer.append(":");
        }

        buffer.append("\"");
        final int maxPosition;
        if (positions.length == 0) {
            maxPosition = -1;
        } else {
            maxPosition = positions[positions.length - 1];
        }
        String[] pieces = new String[maxPosition + 1];
        for (int i = 0; i < terms.length; i++) {
            int pos = positions[i];
            String s = pieces[pos];
            if (s == null) {
                s = (terms[i]).text();
            } else {
                s = s + "|" + (terms[i]).text();
            }
            pieces[pos] = s;
        }
        for (int i = 0; i < pieces.length; i++) {
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

        if (slop != 0) {
            buffer.append("~");
            buffer.append(slop);
        }

        return buffer.toString();
    }

    @Override
    public Query rewrite(IndexSearcher indexSearcher) {
        if (terms.length == 0) {
            return new MatchAllDocsQuery();
        }

        return this;
    }

    @Override
    public void visit(QueryVisitor visitor) {
        visitor.visitLeaf(this);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        return new CustomPhraseWeight(searcher, this);
    }

    private static class CustomPhraseWeight extends Weight {

        private CustomPhraseQuery query;

        private TermStates[] states;

        CustomPhraseWeight(IndexSearcher searcher, CustomPhraseQuery query) throws IOException {
            super(query);
            this.query = query;

            IndexReaderContext context = searcher.getTopReaderContext();

            this.states = new TermStates[query.terms.length];
            for(int i = 0; i < query.terms.length; ++i) {
                Term term = query.terms[i];
                this.states[i] = TermStates.build(context, term, false);
            }
        }

        @Deprecated
        public void extractTerms(Set<Term> set) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Explanation explain(LeafReaderContext leafReaderContext, int i) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            LeafReader reader = context.reader();

            CustomPhraseQuery.PostingsAndFreq[] postingsFreqs = new CustomPhraseQuery.PostingsAndFreq[query.terms.length];

            String field = query.terms[0].field();
            Terms fieldTerms = reader.terms(field);

            if (fieldTerms == null) {
                return null;
            } else if (!fieldTerms.hasPositions()) {
                throw new IllegalStateException("field \"" +field +
                        "\" was indexed without position data; cannot run CustomPhraseQuery (phrase=" + this.getQuery() + ")");
            } else {
                TermsEnum te = fieldTerms.iterator();

                for(int i = 0; i < query.terms.length; ++i) {
                    Term t = query.terms[i];
                    TermState state = this.states[i].get(context);
                    if (state == null) {
                        return null;
                    }

                    te.seekExact(t.bytes(), state);
                    PostingsEnum postingsEnum = te.postings(null, 24);
                    postingsFreqs[i] = new CustomPhraseQuery.PostingsAndFreq(postingsEnum, query.positions[i], t);
                }

                if (query.slop == 0) {
                    ArrayUtil.timSort(postingsFreqs);

                    return new CustomExactPhraseScorer(this, postingsFreqs, query.offset);
                } else {
                    return new CustomSloppyPhraseScorer(this, postingsFreqs, query.slop, query.offset);
                }
            }
        }

        @Override
        public boolean isCacheable(LeafReaderContext leafReaderContext) {
            return false;
        }

    }

    static class PostingsAndFreq implements Comparable<PostingsAndFreq> {
        final PostingsEnum postings;
        final int position;
        final Term[] terms;
        final int nTerms; // for faster comparisons

        PostingsAndFreq(PostingsEnum postings, int position, Term... terms) {
            this.postings = postings;
            this.position = position;
            nTerms = terms==null ? 0 : terms.length;
            if (nTerms>0) {
                if (terms.length==1) {
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

        @Override
        public int compareTo(PostingsAndFreq other) {
            if (position != other.position) {
                return position - other.position;
            }
            if (nTerms != other.nTerms) {
                return nTerms - other.nTerms;
            }
            if (nTerms == 0) {
                return 0;
            }
            for (int i=0; i<terms.length; i++) {
                int res = terms[i].compareTo(other.terms[i]);
                if (res!=0) {
                    return res;
                }
            }
            return 0;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + position;
            for (int i=0; i<nTerms; i++) {
                result = prime * result + terms[i].hashCode();
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            PostingsAndFreq other = (PostingsAndFreq) obj;
            if (position != other.position) {
                return false;
            }
            if (terms == null) {
                return other.terms == null;
            }
            return Arrays.equals(terms, other.terms);
        }
    }
}
