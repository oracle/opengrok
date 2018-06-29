package org.opengrok.suggest;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SuggesterUtils {

    private static final Logger logger = Logger.getLogger(SuggesterUtils.class.getName());

    private static final long DEFAULT_TERM_WEIGHT = 0;

    private static final int NORMALIZED_DOCUMENT_FREQUENCY_MULTIPLIER = 1000;

    private SuggesterUtils() {

    }

    static List<LookupResultItem> combineResults(final List<LookupResultItem> results, final int resultSize) {
        LookupPriorityQueue queue = new LookupPriorityQueue(resultSize);

        Map<String, LookupResultItem> map = new HashMap<>();

        for (LookupResultItem item : results) {
            LookupResultItem storedItem = map.get(item.getPhrase());
            if (storedItem == null) {
                map.put(item.getPhrase(), item);
            } else {
                storedItem.combine(item);
            }
        }

        // `queue` holds only `RESULT_COUNT` items with the highest weight
        map.values().forEach(queue::insertWithOverflow);

        return queue.getResult();
    }

    static long computeWeight(final IndexReader indexReader, final String field, final BytesRef bytesRef) {
        try {
            Term term = new Term(field, bytesRef);
            double normalizedDocumentFrequency = computeNormalizedDocumentFrequency(indexReader, term);

            return (long) (normalizedDocumentFrequency * NORMALIZED_DOCUMENT_FREQUENCY_MULTIPLIER);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not compute weight for " + bytesRef, e);
        }
        return DEFAULT_TERM_WEIGHT;
    }

    private static double computeNormalizedDocumentFrequency(final IndexReader indexReader, final Term term)
            throws IOException {
        int documentFrequency = indexReader.docFreq(term);

        return ((double) documentFrequency) / indexReader.numDocs();
    }


    public static SimpleQueriesHolder intoSimpleQueries(Query query) {
        List<TermQuery> termQueries = new LinkedList<>();
        List<PhraseQuery> phraseQueries = new LinkedList<>();


        LinkedList<Query> queue = new LinkedList<>();
        queue.add(query);

        while (!queue.isEmpty()) {
            Query q = queue.poll();

            if (q instanceof BooleanQuery) {
                for (BooleanClause bc : ((BooleanQuery) q).clauses()) {
                    queue.add(bc.getQuery());
                }
            } else if (q instanceof TermQuery) {
                termQueries.add((TermQuery) q);
            } else if (q instanceof PhraseQuery) {
                phraseQueries.add((PhraseQuery) q);
            }
        }

        return new SimpleQueriesHolder(termQueries, phraseQueries);
    }

    public static class SimpleQueriesHolder {

        public List<TermQuery> termQueries = new LinkedList<>();
        public List<PhraseQuery> phraseQueries = new LinkedList<>();


        public SimpleQueriesHolder(List<TermQuery> termQueries, List<PhraseQuery> phraseQueries) {
            this.termQueries = termQueries;
            this.phraseQueries = phraseQueries;
        }
    }

}
