package org.opengrok.suggest;

import net.openhft.chronicle.map.ChronicleMap;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BytesRef;
import org.opengrok.suggest.query.PhraseScorer;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opengrok.suggest.query.customized.MyPhraseQuery;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

class SuggesterSearcher extends IndexSearcher {

    private static final Logger logger = Logger.getLogger(SuggesterSearcher.class.getName());

    private PhraseScorer scorer;

    private String project;

    private final int resultSize;

    SuggesterSearcher(final IndexReader reader, final int resultSize) {
        super(reader);
        this.resultSize = resultSize;
    }

    public List<LookupResultItem> search(final Query query, final String suggester, final SuggesterQuery suggesterQuery, ChronicleMap<String, Integer> map) {
        this.project = suggester;
        List<LookupResultItem> results = new LinkedList<>();

        Query rewrittenQuery = null;

        try {
            if (query != null) {
                rewrittenQuery = query.rewrite(getIndexReader());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not rewrite query", e);
            return results;
        }

        for (LeafReaderContext context : this.leafContexts) {
            try {
                results.addAll(search(rewrittenQuery, context, suggester, suggesterQuery, map));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Cannot perform suggester search", e);
            }
        }

        if (results.size() > resultSize) {
            return SuggesterUtils.combineResults(results, resultSize);
        }

        return results;
    }

    public List<LookupResultItem> search(
            final Query query,
            final LeafReaderContext leafReaderContext,
            final String suggester,
            final SuggesterQuery suggesterQuery,
            final ChronicleMap<String, Integer> map
    ) throws IOException {

        boolean needsDocumentIds = query != null && !(query instanceof MatchAllDocsQuery);

        BitSet documentIds = null;
        if (needsDocumentIds) {
            documentIds = getDocumentIds(query, leafReaderContext);
        }

        Terms terms = leafReaderContext.reader().terms(suggesterQuery.getField());

        TermsEnum termsEnum = suggesterQuery.getTermsEnumForSuggestions(terms);

        LookupPriorityQueue queue = new LookupPriorityQueue(resultSize);

        BytesRef term = termsEnum.next();

        boolean needPositionsAndFrequencies = needPositionsAndFrequencies(query);

        Map<Integer, BitSet> map2 = new HashMap<>();

        PostingsEnum postingsEnum = null;
        while (term != null) {
            if (needPositionsAndFrequencies) {
                postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.POSITIONS | PostingsEnum.FREQS);
            } else {
                postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.NONE);
            }

            int weight;
            if (!needsDocumentIds) {
                weight = termsEnum.docFreq();
            } else if (needPositionsAndFrequencies) {
                weight = getPhraseScore(documentIds, leafReaderContext.docBase, postingsEnum, query, map2);
            } else {
                weight = getDocumentFrequency(documentIds, leafReaderContext.docBase, postingsEnum);
            }

            if (weight > 0) {

                Integer add = map.get(term.utf8ToString());
                //if (add > 0) {
                //    System.out.println();
                //}
                if (add != null) {
                    weight += add * 1000;
                }
                queue.insertWithOverflow(new LookupResultItem(term.utf8ToString(), suggester, weight));
            }

            term = termsEnum.next();
        }

        return queue.getResult();
    }

    private BitSet getDocumentIds(Query query, LeafReaderContext leafReaderContext) {
        if (query == null || query instanceof SuggesterQuery) {
            return new BitSet();
        }

        BitSet documentIds = new BitSet();

        try {
            search(query, new Collector() {
                @Override
                public LeafCollector getLeafCollector(LeafReaderContext context) {
                    return new LeafCollector() {

                        final int docBase = context.docBase;

                        @Override
                        public void setScorer(Scorer scorer) {
                            //SuggesterSearcher.this.scorer = scorer;
                            if (leafReaderContext == context) {
                                if (scorer instanceof PhraseScorer) {
                                    SuggesterSearcher.this.scorer = (PhraseScorer) scorer;
                                } else {
                                    try {
                                        // it is mentioned in the documentation that #getChildren should not be called
                                        // in #setScorer but no better way was found
                                        for (Scorer.ChildScorer childScorer : scorer.getChildren()) {
                                            if (childScorer.child instanceof PhraseScorer) {
                                                SuggesterSearcher.this.scorer = (PhraseScorer) childScorer.child;
                                            }
                                        }
                                    } catch (Exception e) {
                                        // ignore
                                    }
                                }
                            }

                            //System.out.println();
                        }

                        @Override
                        public void collect(int doc) {
                            if (leafReaderContext == context) {
                                documentIds.set(docBase + doc);
                            }
                        }
                    };
                }

                @Override
                public boolean needsScores() {
                    return false;
                }
            });
        } catch (Exception e) {
            logger.log(Level.WARNING, project, e);
        }

        return documentIds;
    }

    private int getPhraseScore(final BitSet documentIds, final int docBase, final PostingsEnum postingsEnum, final Query query, Map<Integer, BitSet> map)
            throws IOException {

        //if (scorer == null) {
            // no documents found
        //    return 0;
        //}

        int weight = 0;
        while (postingsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            int docId = postingsEnum.docID();
            if (documentIds.get(docBase + docId)) {
                //Set<Integer> positions = scorer.getMap().get(docId);
                //if (positions == null) {
                //    logger.log(Level.WARNING, "No positions entry: " + docId);
                //    continue;
                //}
                /*if (!map.containsKey(docBase + docId)) {

                    Matches m = scorer.getWeight().matches(leafContexts.get(0), docBase + docId);
                    //Matches m = query.createWeight(this, false, 1)
                    //        .matches(leafContexts.get(0), docBase + docId);
                    BitSet set = new BitSet();

                    MatchesIterator it = m.getMatches("full");
                    if (it != null) {
                        while (it.next()) {
                            set.set(it.startPosition() + ((MyPhraseQuery) query).offset);
                        }
                    }
                    map.put(docBase + docId, set);
                }*/

                Set<Integer> set2 = scorer.getMap().get(docBase + docId);

                BitSet set = map.get(docBase + docId);

                if (set2 == null) {
                    continue;
                }

                int freq = postingsEnum.freq();
                for (int i = 0; i < freq; i++) {
                    int pos = postingsEnum.nextPosition();

                    if (set2.contains(pos)) { //if (set.get(pos)) {
                        weight++;
                    }
                }
            }
        }

        return weight;
    }

    private int getDocumentFrequency(final BitSet documentIds, final int docBase, final PostingsEnum postingsEnum)
            throws IOException {

        int weight = 0;
        while (postingsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            if (documentIds.get(docBase + postingsEnum.docID())) {
                weight++;
            }
        }
        return weight;
    }

    private boolean needPositionsAndFrequencies(Query query) {
        if (query instanceof MyPhraseQuery) {
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

}
