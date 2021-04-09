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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.opengrok.indexer.analysis.CompatibleAnalyser;
import org.opengrok.indexer.analysis.AccumulatedNumLinesLOC;
import org.opengrok.indexer.analysis.NullableNumLinesLOC;
import org.opengrok.indexer.analysis.NumLinesLOC;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.NumberUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Represents a data-access object for Lucene documents containing directory
 * number-of-lines and lines-of-code data.
 */
class NumLinesLOCAccessor {
    private static final int BULK_READ_THRESHOLD = 100;

    /**
     * Determines whether there is stored number-of-lines and lines-of-code
     * in the index associated to the specified {@code reader}.
     */
    public boolean hasStored(IndexReader reader) throws IOException {
        DSearchResult searchResult = newDSearch(reader, 1);
        return searchResult.hits.totalHits.value > 0;
    }

    /**
     * Stores the net deltas to the index through the specified {@code writer}.
     */
    public void store(IndexWriter writer, IndexReader reader,
            NumLinesLOCAggregator countsAggregator, boolean isAggregatingDeltas)
            throws IOException {

        List<AccumulatedNumLinesLOC> counts = new ArrayList<>();
        countsAggregator.iterator().forEachRemaining(counts::add);
        if (counts.size() >= BULK_READ_THRESHOLD) {
            storeBulk(writer, reader, counts, isAggregatingDeltas);
        } else if (counts.size() > 0) {
            storeIterative(writer, reader, counts, isAggregatingDeltas);
        }
    }

    /**
     * Queries the stored counts from the specified reader to register them to
     * the specified aggregator.
     * @return a value indicating whether any defined number-of-lines and
     * lines-of-code were found
     */
    public boolean register(NumLinesLOCAggregator countsAggregator, IndexReader reader)
            throws IOException {

        /*
         * Search for existing documents with any value of PATH. Those are
         * documents representing source code files, as opposed to source code
         * directories or other object data (e.g. IndexAnalysisSettings3), which
         * have no stored PATH.
         */
        IndexSearcher searcher = new IndexSearcher(reader);

        Query query;
        try {
            QueryParser parser = new QueryParser(QueryBuilder.PATH, new CompatibleAnalyser());
            parser.setAllowLeadingWildcard(true);
            query = parser.parse("*");
        } catch (ParseException ex) {
            // This is not expected, so translate to RuntimeException.
            throw new RuntimeException(ex);
        }

        TopDocs hits = searcher.search(query, Integer.MAX_VALUE);
        return processFileCounts(countsAggregator, searcher, hits);
    }

    private void storeBulk(IndexWriter writer, IndexReader reader,
            List<AccumulatedNumLinesLOC> counts, boolean isAggregatingDeltas) throws IOException {

        DSearchResult searchResult = newDSearch(reader, Integer.MAX_VALUE);

        // Index the existing document IDs by QueryBuilder.D.
        HashMap<String, Integer> byDir = new HashMap<>();
        int intMaximum = Integer.MAX_VALUE < searchResult.hits.totalHits.value ?
                Integer.MAX_VALUE : (int) searchResult.hits.totalHits.value;
        for (int i = 0; i < intMaximum; ++i) {
            int docID = searchResult.hits.scoreDocs[i].doc;
            Document doc = searchResult.searcher.doc(docID);
            String dirPath = doc.get(QueryBuilder.D);
            byDir.put(dirPath, docID);
        }

        for (AccumulatedNumLinesLOC entry : counts) {
            Integer docID = byDir.get(entry.getPath());
            updateDocumentData(writer, searchResult.searcher, entry, docID, isAggregatingDeltas);
        }
    }

    private void storeIterative(IndexWriter writer, IndexReader reader,
            List<AccumulatedNumLinesLOC> counts, boolean isAggregatingDeltas) throws IOException {

        // Search for existing documents with QueryBuilder.D.
        IndexSearcher searcher = new IndexSearcher(reader);

        for (AccumulatedNumLinesLOC entry : counts) {
            Query query = new TermQuery(new Term(QueryBuilder.D, entry.getPath()));
            TopDocs hits = searcher.search(query, 1);

            Integer docID = null;
            if (hits.totalHits.value > 0) {
                docID = hits.scoreDocs[0].doc;
            }
            updateDocumentData(writer, searcher, entry, docID, isAggregatingDeltas);
        }
    }

    private void updateDocumentData(IndexWriter writer, IndexSearcher searcher,
            AccumulatedNumLinesLOC aggregate, Integer docID, boolean isAggregatingDeltas)
            throws IOException {

        File pathFile = new File(aggregate.getPath());
        String parent = pathFile.getParent();
        if (parent == null) {
            parent = "";
        }

        String normalizedPath = QueryBuilder.normalizeDirPath(parent);
        long extantLOC = 0;
        long extantLines = 0;

        if (docID != null) {
            Document doc = searcher.doc(docID);
            if (isAggregatingDeltas) {
                extantLines = NumberUtil.tryParseLongPrimitive(doc.get(QueryBuilder.NUML));
                extantLOC = NumberUtil.tryParseLongPrimitive(doc.get(QueryBuilder.LOC));
            }
            writer.deleteDocuments(new Term(QueryBuilder.D, aggregate.getPath()));
        }

        long newNumLines = extantLines + aggregate.getNumLines();
        long newLOC = extantLOC + aggregate.getLOC();

        Document doc = new Document();
        doc.add(new StringField(QueryBuilder.D, aggregate.getPath(), Field.Store.YES));
        doc.add(new StringField(QueryBuilder.DIRPATH, normalizedPath, Field.Store.NO));
        doc.add(new StoredField(QueryBuilder.NUML, newNumLines));
        doc.add(new StoredField(QueryBuilder.LOC, newLOC));
        writer.addDocument(doc);
    }

    private boolean processFileCounts(NumLinesLOCAggregator countsAggregator,
            IndexSearcher searcher, TopDocs hits) throws IOException {

        boolean hasDefinedNumLines = false;
        for (ScoreDoc sd : hits.scoreDocs) {
            Document d = searcher.doc(sd.doc);
            NullableNumLinesLOC counts = NumLinesLOCUtil.read(d);
            if (counts.getNumLines() != null && counts.getLOC() != null) {
                NumLinesLOC defCounts = new NumLinesLOC(counts.getPath(),
                        counts.getNumLines(), counts.getLOC());
                countsAggregator.register(defCounts);
                hasDefinedNumLines = true;
            }
        }
        return hasDefinedNumLines;
    }

    private DSearchResult newDSearch(IndexReader reader, int n) throws IOException {
        // Search for existing documents with QueryBuilder.D.
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query;
        try {
            QueryParser parser = new QueryParser(QueryBuilder.D, new CompatibleAnalyser());
            parser.setAllowLeadingWildcard(true);
            query = parser.parse("*");
        } catch (ParseException ex) {
            // This is not expected, so translate to RuntimeException.
            throw new RuntimeException(ex);
        }

        TopDocs topDocs = searcher.search(query, n);
        return new DSearchResult(searcher, topDocs);
    }

    private static class DSearchResult {
        private final IndexSearcher searcher;
        private final TopDocs hits;

        DSearchResult(IndexSearcher searcher, TopDocs hits) {
            this.searcher = searcher;
            this.hits = hits;
        }
    }
}
