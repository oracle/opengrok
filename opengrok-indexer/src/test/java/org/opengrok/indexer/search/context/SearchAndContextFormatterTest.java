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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search.context;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;

import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.plain.PlainAnalyzerFactory;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.search.SearchEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;

/**
 * Represents a container for tests of {@link SearchEngine} with
 * {@link ContextFormatter} etc.
 * <p>
 * Derived from Trond Norbye's {@code SearchEngineTest}
 */
class SearchAndContextFormatterTest {

    private static RuntimeEnvironment env;
    private static TestRepository repository;
    private static File configFile;

    @BeforeAll
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/repositories"));

        env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty("org.opengrok.indexer.analysis.Ctags", "ctags"));
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        RepositoryFactory.initializeIgnoredNames(env);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(false);
        Indexer.getInstance().prepareIndexer(env, true, true,
                false, null, null);
        env.setDefaultProjectsFromNames(new TreeSet<>(Collections.singletonList("/c")));
        Indexer.getInstance().doIndexerExecution(true, null, null);

        configFile = File.createTempFile("configuration", ".xml");
        env.writeConfiguration(configFile);
        RuntimeEnvironment.getInstance().readConfiguration(new File(configFile.getAbsolutePath()));
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
        configFile.delete();
    }

    @Test
    void testSearch() throws IOException {
        SearchEngine instance = new SearchEngine();
        instance.setFreetext("mt19937");
        instance.setFile("bkexlib.cpp");
        int noHits = instance.search();
        assertTrue(noHits > 0, "noHits should be positive");
        String[] frags = getFirstFragments(instance);
        assertNotNull(frags, "getFirstFragments() should return something");
        assertEquals(1, frags.length, "frags should have one element");

        final String CTX = "<a class=\"s\" href=\"/source/bitkeeper/bkexlib.cpp#12\"><span class=\"l\">12</span>"
                + "     std::random_device rd;</a><br/><a class=\"s\" href=\"/source/bitkeeper/bkexlib.cpp#13\">"
                + "<span class=\"l\">13</span>     std::<b>mt19937</b> gen(rd());</a><br/><a class=\"s\" "
                + "href=\"/source/bitkeeper/bkexlib.cpp#14\"><span class=\"l\">14</span>     "
                + "std::uniform_int_distribution&lt;&gt; dis(0, RAND_MAX);</a><br/>";
        assertLinesEqual("ContextFormatter output", CTX, frags[0]);
        instance.destroy();
    }

    private String[] getFirstFragments(SearchEngine instance) throws IOException {

        ContextArgs args = new ContextArgs((short) 1, (short) 10);

        /*
         * The following `anz' should go unused, but UnifiedHighlighter demands
         * an analyzer "even if in some circumstances it isn't used."
         */
        PlainAnalyzerFactory fac = PlainAnalyzerFactory.DEFAULT_INSTANCE;
        AbstractAnalyzer anz = fac.getAnalyzer();

        ContextFormatter formatter = new ContextFormatter(args);
        UnifiedHighlighter.Builder uhBuilder =  new UnifiedHighlighter.Builder(instance.getSearcher(), anz)
//                .withMaxLength(maxDocCharsToAnalyze)
//                .withHighlightPhrasesStrictly(true)
//                .withHandleMultiTermQuery(true)
                .withBreakIterator(StrictLineBreakIterator::new)
                .withFormatter(formatter);
        OGKUnifiedHighlighter uhi = new OGKUnifiedHighlighter(env, uhBuilder);

        ScoreDoc[] docs = instance.scoreDocs();
        for (ScoreDoc scoreDoc : docs) {
            int docid = scoreDoc.doc;
            Document doc = instance.doc(docid);

            String path = doc.get(QueryBuilder.PATH);
            System.out.println(path);
            formatter.setUrl("/source" + path);

            for (String contextField : instance.getQueryBuilder().getContextFields()) {

                Map<String, String[]> res = uhi.highlightFields(
                        new String[] {contextField}, instance.getQueryObject(),
                        new int[] {docid}, new int[] {10});
                String[] frags = res.getOrDefault(contextField, null);
                if (frags != null) {
                    return frags;
                }
            }
        }
        return null;
    }
}
