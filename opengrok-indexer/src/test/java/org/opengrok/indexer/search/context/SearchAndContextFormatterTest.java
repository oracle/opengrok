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
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.After;
import org.junit.AfterClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.plain.PlainAnalyzerFactory;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.search.SearchEngine;

import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;

/**
 * Represents a container for tests of {@link SearchEngine} with
 * {@link ContextFormatter} etc.
 * <p>
 * Derived from Trond Norbye's {@code SearchEngineTest}
 */
public class SearchAndContextFormatterTest {

    private static RuntimeEnvironment env;
    private static TestRepository repository;
    private static File configFile;

    @BeforeClass
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream("repositories.zip"));

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
        env.setDefaultProjectsFromNames(new TreeSet<String>(Collections.singletonList("/c")));
        Indexer.getInstance().doIndexerExecution(true, null, null);

        configFile = File.createTempFile("configuration", ".xml");
        env.writeConfiguration(configFile);
        RuntimeEnvironment.getInstance().readConfiguration(new File(configFile.getAbsolutePath()));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        repository.destroy();
        configFile.delete();
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testSearch() throws IOException {
        SearchEngine instance = new SearchEngine();
        instance.setFreetext("embedded");
        instance.setFile("main.c");
        int noHits = instance.search();
        assertTrue("noHits should be positive", noHits > 0);
        String[] frags = getFirstFragments(instance);
        assertNotNull("getFirstFragments() should return something", frags);
        assertEquals("frags should have one element", 1, frags.length);

        final String CTX =
                "<a class=\"s\" href=\"/source/svn/c/main.c#9\"><span class=\"l\">9</span>    /*</a><br/>" +
                        "<a class=\"s\" href=\"/source/svn/c/main.c#10\"><span class=\"l\">10</span>    " +
                        "Multi line comment, with <b>embedded</b> strange characters: &lt; &gt; &amp;,</a><br/>" +
                        "<a class=\"s\" href=\"/source/svn/c/main.c#11\"><span class=\"l\">11</span>    " +
                        "email address: testuser@example.com and even an URL:</a><br/>";
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
        OGKUnifiedHighlighter uhi = new OGKUnifiedHighlighter(env, instance.getSearcher(), anz);
        uhi.setBreakIterator(StrictLineBreakIterator::new);
        uhi.setFormatter(formatter);

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
