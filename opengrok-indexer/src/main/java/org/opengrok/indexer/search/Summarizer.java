/*
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// modified by Lubos Kosco 2010 to upgrade lucene to 3.0.0
// modified by Lubos Kosco 2014 to upgrade lucene to 4.9.0
// TODO : rewrite this to use Highlighter from lucene contrib ...
package org.opengrok.indexer.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PackedTokenAttributeImpl;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;

/**
 * Implements hit summarization.
 */
public class Summarizer {

    /**
     * The number of context terms to display preceding and following matches.
     */
    private static final int SUM_CONTEXT = 10;

    /**
     * The total number of terms to display in a summary.
     */
    private static final int SUM_LENGTH = 20;

    /**
     * Converts text to tokens.
     */
    private final Analyzer analyzer;

    private final Set<String> highlight = new HashSet<>();            // put query terms in table

    public Summarizer(Query query, Analyzer a) {
        analyzer = a;
        getTerms(query);
    }

    /**
     * Class Excerpt represents a single passage found in the document, with
     * some appropriate regions highlight.
     */
    static class Excerpt {

        List<Summary.Fragment> passages = new ArrayList<>();
        Set<String> tokenSet = new TreeSet<>();
        int numTerms = 0;

        public void addToken(String token) {
            tokenSet.add(token);
        }

        /**
         * Return how many unique tokens we have.
         */
        public int numUniqueTokens() {
            return tokenSet.size();
        }

        /**
         * How many fragments we have.
         */
        public int numFragments() {
            return passages.size();
        }

        public void setNumTerms(int numTerms) {
            this.numTerms = numTerms;
        }

        public int getNumTerms() {
            return numTerms;
        }

        /**
         * Add a frag to the list.
         */
        public void add(Summary.Fragment fragment) {
            passages.add(fragment);
        }

        /**
         * Return an Enum for all the fragments.
         */
        public List<Summary.Fragment> elements() {
            return passages;
        }
    }

    /**
     * Returns a summary for the given pre-tokenized text.
     *
     * @param text input text
     * @return summary of hits
     * @throws java.io.IOException I/O exception
     */
    public Summary getSummary(String text) throws IOException {
        if (text == null) {
            return null;
        }
        // Simplistic implementation.  Finds the first fragments in the document
        // containing any query terms.
        //
        // @TODO: check that phrases in the query are matched in the fragment

        SToken[] tokens = getTokens(text);             // parse text to token array

        if (tokens.length == 0) {
            return new Summary();
        }

        //
        // Create a SortedSet that ranks excerpts according to
        // how many query terms are present.  An excerpt is
        // a List full of Fragments and Highlights
        //
        SortedSet<Excerpt> excerptSet = new TreeSet<>((excerpt1, excerpt2) -> {
            if (excerpt1 == null) {
                return excerpt2 == null ? 0 : -1;
            } else if (excerpt2 == null) {
                return 1;
            } else {
                int numToks1 = excerpt1.numUniqueTokens();
                int numToks2 = excerpt2.numUniqueTokens();

                if (numToks1 < numToks2) {
                    return -1;
                } else if (numToks1 == numToks2) {
                    return excerpt1.numFragments() - excerpt2.numFragments();
                } else {
                    return 1;
                }
            }
        });

        //
        // Iterate through all terms in the document
        //
        int lastExcerptPos = 0;
        for (int i = 0; i < tokens.length; i++) {
            //
            // If we find a term that's in the query...
            //
            if (highlight.contains(tokens[i].toString())) {
                //
                // Start searching at a point SUM_CONTEXT terms back,
                // and move SUM_CONTEXT terms into the future.
                //
                int startToken = (i > SUM_CONTEXT) ? i - SUM_CONTEXT : 0;
                int endToken = Math.min(i + SUM_CONTEXT, tokens.length);
                int offset = tokens[startToken].startOffset();
                int j = startToken;

                //
                // Iterate from the start point to the finish, adding
                // terms all the way.  The end of the passage is always
                // SUM_CONTEXT beyond the last query-term.
                //
                Excerpt excerpt = new Excerpt();
                if (i != 0) {
                    excerpt.add(new Summary.Ellipsis());
                }

                //
                // Iterate through as long as we're before the end of
                // the document and we haven't hit the max-number-of-items
                // -in-a-summary.
                //
                while ((j < endToken) && (j - startToken < SUM_LENGTH)) {
                    //
                    // Now grab the hit-element, if present
                    //
                    SToken t = tokens[j];
                    if (highlight.contains(t.toString())) {
                        excerpt.addToken(t.toString());
                        excerpt.add(new Summary.Fragment(text.substring(offset, t.startOffset())));
                        excerpt.add(new Summary.Highlight(text.substring(t.startOffset(), t.endOffset())));
                        offset = t.endOffset();
                        endToken = Math.min(j + SUM_CONTEXT, tokens.length);
                    }

                    j++;
                }

                lastExcerptPos = endToken;

                //
                // We found the series of search-term hits and added
                // them (with intervening text) to the excerpt.  Now
                // we need to add the trailing edge of text.
                //
                // So if (j < tokens.length) then there is still trailing
                // text to add.  (We haven't hit the end of the source doc.)
                // Add the words since the last hit-term insert.
                //
                if (j < tokens.length) {
                    excerpt.add(new Summary.Fragment(text.substring(offset, tokens[j].endOffset())));
                }

                //
                // Remember how many terms are in this excerpt
                //
                excerpt.setNumTerms(j - startToken);

                //
                // Store the excerpt for later sorting
                //
                excerptSet.add(excerpt);

                //
                // Start SUM_CONTEXT places away.  The next
                // search for relevant excerpts begins at i-SUM_CONTEXT
                //
                i = j + SUM_CONTEXT;
            }
        }

        //
        // If the target text doesn't appear, then we just
        // excerpt the first SUM_LENGTH words from the document.
        //
        if (excerptSet.isEmpty()) {
            Excerpt excerpt = new Excerpt();
            int excerptLen = Math.min(SUM_LENGTH, tokens.length);
            lastExcerptPos = excerptLen;

            excerpt.add(new Summary.Fragment(text.substring(tokens[0].startOffset(), tokens[excerptLen - 1].startOffset())));
            excerpt.setNumTerms(excerptLen);
            excerptSet.add(excerpt);
        }

        //
        // Now choose the best items from the excerpt set.
        // Stop when our Summary grows too large.
        //
        double tokenCount = 0;
        Summary s = new Summary();
        while (tokenCount <= SUM_LENGTH && excerptSet.size() > 0) {
            Excerpt excerpt = excerptSet.last();
            excerptSet.remove(excerpt);

            double tokenFraction = (1.0 * excerpt.getNumTerms()) / excerpt.numFragments();
            for (Summary.Fragment f : excerpt.elements()) {
                // Don't add fragments if it takes us over the max-limit
                if (tokenCount + tokenFraction <= SUM_LENGTH) {
                    s.add(f);
                }
                tokenCount += tokenFraction;
            }
        }

        if (tokenCount > 0 && lastExcerptPos < tokens.length) {
            s.add(new Summary.Ellipsis());
        }
        return s;
    }

    private static class SToken extends PackedTokenAttributeImpl {

        SToken(char[] startTermBuffer, int termBufferOffset, int termBufferLength, int start, int end) {
            copyBuffer(startTermBuffer, termBufferOffset, termBufferLength);
            setOffset(start, end);
        }
    }

    private SToken[] getTokens(String text) throws IOException {
        //FIXME somehow integrate below cycle to getSummary to save the cloning and memory,
        //also creating Tokens is suboptimal with 3.0.0 , this whole class could be replaced by highlighter
        ArrayList<SToken> result = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream(QueryBuilder.FULL, text)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                SToken t = new SToken(term.buffer(), 0, term.length(), offset.startOffset(), offset.endOffset());
                result.add(t);
            }
            ts.end();
        }
        return result.toArray(new SToken[0]);
    }

    /**
     * Get the terms from a query and adds them to highlight a stream of tokens.
     */
    private void getTerms(Query query) {
        if (query instanceof BooleanQuery) {
            getBooleans((BooleanQuery) query);
        } else if (query instanceof PhraseQuery) {
            getPhrases((PhraseQuery) query);
        } else if (query instanceof WildcardQuery) {
            getWildTerm((WildcardQuery) query);
        } else if (query instanceof TermQuery) {
            getTerm((TermQuery) query);
        } else if (query instanceof PrefixQuery) {
            getPrefix((PrefixQuery) query);
        }
    }

    private void getBooleans(BooleanQuery query) {
        for (BooleanClause clause : query) {
            if (!clause.isProhibited()) {
                getTerms(clause.getQuery());
            }
        }
    }

    private void getPhrases(PhraseQuery query) {
        Term[] queryTerms = query.getTerms();
        for (Term queryTerm : queryTerms) {
            highlight.add(queryTerm.text());
        }
    }

    private void getTerm(TermQuery query) {
        highlight.add(query.getTerm().text());
    }

    private void getWildTerm(WildcardQuery query) {
        highlight.add(query.getTerm().text());
    }

    private void getPrefix(PrefixQuery query) {
        highlight.add(query.getPrefix().text());
    }
}
