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
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public class MyPhraseQuery extends PhraseQuery {

    public int offset;

    public MyPhraseQuery(int slop, String field, String... terms) {
        super(slop, field, terms);
    }

    public MyPhraseQuery(String field, String... terms) {
        super(field, terms);
    }

    public MyPhraseQuery(int slop, String field, BytesRef... terms) {
        super(slop, field, terms);
    }

    public MyPhraseQuery(String field, BytesRef... terms) {
        super(field, terms);
    }

    public Query rewrite(IndexReader reader) {
        if (getTerms().length == 0) {
            return new MatchAllDocsQuery();
        }

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MyPhraseQuery that = (MyPhraseQuery) o;
        return offset == that.offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), offset);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
        return new MyPhraseWeight(searcher, this);
    }

    private static class MyPhraseWeight extends Weight {

        private MyPhraseQuery query;

        private TermContext[] states;

        MyPhraseWeight(IndexSearcher searcher, MyPhraseQuery query) throws IOException {
            super(query);
            this.query = query;

            IndexReaderContext context = searcher.getTopReaderContext();

            this.states = new TermContext[query.getTerms().length];
            for(int i = 0; i < query.getTerms().length; ++i) {
                Term term = query.getTerms()[i];
                this.states[i] = TermContext.build(context, term);
            }
        }

        @Override
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

            MyPhraseQuery.PostingsAndFreq[] postingsFreqs = new MyPhraseQuery.PostingsAndFreq[query.getTerms().length];

            String field = query.getTerms()[0].field();
            Terms fieldTerms = reader.terms(field);

            if (fieldTerms == null) {
                return null;
            } else if (!fieldTerms.hasPositions()) {
                throw new IllegalStateException("field \"" +field +
                        "\" was indexed without position data; cannot run MyPhraseQuery (phrase=" + this.getQuery() + ")");
            } else {
                TermsEnum te = fieldTerms.iterator();

                for(int i = 0; i < query.getTerms().length; ++i) {
                    Term t = query.getTerms()[i];
                    TermState state = this.states[i].get(context.ord);
                    if (state == null) {
                        return null;
                    }

                    te.seekExact(t.bytes(), state);
                    PostingsEnum postingsEnum = te.postings(null, 24);
                    postingsFreqs[i] = new MyPhraseQuery.PostingsAndFreq(postingsEnum, query.getPositions()[i], t);
                }

                if (query.getSlop() == 0) {
                    ArrayUtil.timSort(postingsFreqs);

                    return new MyExactPhraseScorer(this, postingsFreqs, query.offset);
                } else {
                    return new MySloppyPhraseScorer(this, postingsFreqs, query.getSlop(), query.offset);
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
                if (res!=0) return res;
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
