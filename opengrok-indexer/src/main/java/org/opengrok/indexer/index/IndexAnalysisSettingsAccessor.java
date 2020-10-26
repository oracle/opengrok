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
 * Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.opengrok.indexer.analysis.CompatibleAnalyser;
import org.opengrok.indexer.search.QueryBuilder;

/**
 * Represents a data-access object for {@link IndexAnalysisSettings}.
 */
public class IndexAnalysisSettingsAccessor {

    /**
     * The {@link QueryBuilder}-normalized value for UUID 58859C75-F941-42E5-8D1A-FAF71DDEBBA7.
     */
    static final String INDEX_ANALYSIS_SETTINGS_OBJUID = "uthuslvotkgltggqqjmurqojpjpjjkutkujktnkk";

    private static final int INDEX_ANALYSIS_SETTINGS_OBJVER = 3;

    /**
     * Searches for a document with a {@link QueryBuilder#OBJUID} value matching
     * {@link #INDEX_ANALYSIS_SETTINGS_OBJUID}. The first document found is
     * returned, so this should not be called with a MultiReader.
     * @param reader a defined instance
     * @return a defined instance or {@code null} if none could be found
     * @throws IOException if I/O error occurs while searching Lucene
     */
    public IndexAnalysisSettings3 read(IndexReader reader) throws IOException {
        IndexAnalysisSettings3[] res = read(reader, 1);
        return res.length > 0 ? res[0] : null;
    }

    /**
     * Searches for documents with a {@link QueryBuilder#OBJUID} value matching
     * {@link #INDEX_ANALYSIS_SETTINGS_OBJUID}.
     * @param reader a defined instance
     * @param n a limit to the number of documents returned. The method may
     * return less.
     * @return a defined instance, which is empty if none could be found
     * @throws IOException if I/O error occurs while searching Lucene
     */
    public IndexAnalysisSettings3[] read(IndexReader reader, int n)
            throws IOException {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q;
        try {
            q = new QueryParser(QueryBuilder.OBJUID, new CompatibleAnalyser()).
                parse(INDEX_ANALYSIS_SETTINGS_OBJUID);
        } catch (ParseException ex) {
            // This is not expected, so translate to RuntimeException.
            throw new RuntimeException(ex);
        }
        TopDocs top = searcher.search(q, n);

        int nres = top.totalHits.value > n ? n : (int) top.totalHits.value;
        IndexAnalysisSettings3[] res = new IndexAnalysisSettings3[nres];

        IndexAnalysisSettingsUpgrader upgrader = new IndexAnalysisSettingsUpgrader();
        for (int i = 0; i < nres; ++i) {
            Document doc = searcher.doc(top.scoreDocs[i].doc);
            IndexableField objser = doc.getField(QueryBuilder.OBJSER);
            int objver = readObjectVersion(doc);
            try {
                res[i] = objser == null ? null : upgrader.upgrade(
                        objser.binaryValue().bytes, objver);
            } catch (ClassNotFoundException ex) {
                // This is not expected, so translate to RuntimeException.
                throw new RuntimeException(ex);
            }
        }
        return res;
    }

    /**
     * Writes a document to contain the serialized version of {@code settings},
     * with a {@link QueryBuilder#OBJUID} value set to
     * {@link #INDEX_ANALYSIS_SETTINGS_OBJUID}. An existing version of the
     * document is first deleted.
     * @param writer a defined, target instance
     * @param settings a defined instance
     * @throws IOException if I/O error occurs while writing Lucene
     */
    public void write(IndexWriter writer, IndexAnalysisSettings3 settings)
            throws IOException {
        byte[] objser = settings.serialize();

        writer.deleteDocuments(new Term(QueryBuilder.OBJUID,
            INDEX_ANALYSIS_SETTINGS_OBJUID));

        Document doc = new Document();
        StringField uidfield = new StringField(QueryBuilder.OBJUID,
            INDEX_ANALYSIS_SETTINGS_OBJUID, Field.Store.NO);
        doc.add(uidfield);
        doc.add(new StoredField(QueryBuilder.OBJSER, objser));
        doc.add(new StoredField(QueryBuilder.OBJVER,
            INDEX_ANALYSIS_SETTINGS_OBJVER));
        writer.addDocument(doc);
    }

    private int readObjectVersion(Document doc) {
        IndexableField objver = doc.getField(QueryBuilder.OBJVER);
        return objver == null ? 1 : objver.numericValue().intValue();
    }
}
