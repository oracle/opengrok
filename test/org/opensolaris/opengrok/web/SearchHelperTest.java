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
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.Indexer;
import org.opensolaris.opengrok.index.IndexerTest;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Unit tests for the {@code SearchHelper} class.
 */
public class SearchHelperTest {
    TestRepository repository;
    private final String ctagsProperty = "org.opensolaris.opengrok.analysis.Ctags";
    RuntimeEnvironment env;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws IOException {
        repository = new TestRepository();
        repository.create(IndexerTest.class.getResourceAsStream("source.zip"));

        env = RuntimeEnvironment.getInstance();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setVerbose(true);
    }

    @After
    public void tearDown() {
        repository.destroy();
    }

    private void reindex() throws Exception {
        System.out.println("Generating index by using the class methods");

        Indexer.getInstance().prepareIndexer(env, true, true, "/c", null,
            false, false, false, null, null, new ArrayList<>(), false);
        Indexer.getInstance().doIndexerExecution(true, 1, null, null);
    }

    private SearchHelper getSearchHelper(String searchTerm) {
        SearchHelper sh = new SearchHelper();

        sh.dataRoot = env.getDataRootFile(); // throws Exception if none-existent
        sh.order = SortOrder.RELEVANCY;
        sh.builder = new QueryBuilder().setFreetext(searchTerm);
        Assert.assertNotSame(0, sh.builder.getSize());
        sh.start = 0;
        sh.maxItems = env.getHitsPerPage();
        sh.contextPath = env.getUrlPrefix();
        sh.parallel = Runtime.getRuntime().availableProcessors() > 1;
        sh.isCrossRefSearch = false;
        sh.compressed = env.isCompressXref();
        sh.desc = null;
        sh.sourceRoot = env.getSourceRootFile();
        sh.lastEditedDisplayMode = false;

        return sh;
    }

    @Test
    public void testSearchAfterReindex() {
        SortedSet<String> projects = new TreeSet<>();
        SearchHelper searchHelper;

        env.setCtags(System.getProperty(ctagsProperty, "ctags"));
        if (!env.validateExuberantCtags()) {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
            return;
        }

        try {
            reindex();
        } catch (Exception ex) {
            Assert.fail("failed to reindex: " + ex);
        }

        // Search for existing term in single project.
        projects.add("/c");
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projects).executeQuery().prepareSummary();
        Assert.assertNull(searchHelper.errorMsg);
        System.out.println("single project search returned " +
            Integer.toString(searchHelper.totalHits) + " hits");
        Assert.assertEquals(4, searchHelper.totalHits);
        searchHelper.destroy();

        // Search for existing term in multiple projects.
        projects.add("/document");
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projects).executeQuery().prepareSummary();
        Assert.assertNull(searchHelper.errorMsg);
        System.out.println("multi-project search returned " +
            Integer.toString(searchHelper.totalHits) + " hits");
        Assert.assertEquals(5, searchHelper.totalHits);
        searchHelper.destroy();

        // Search for non-existing term.
        searchHelper = this.getSearchHelper("CannotExistAnywhereForSure")
            .prepareExec(projects).executeQuery().prepareSummary();
        Assert.assertNull(searchHelper.errorMsg);
        System.out.println("multi-project search for non-existing term returned " +
            Integer.toString(searchHelper.totalHits) + " hits");
        Assert.assertEquals(0, searchHelper.totalHits);
        searchHelper.destroy();

        // Add a change to the repository, reindex, try to reopen the indexes
        // and repeat the search.
        try {
            repository.addDummyFile("c", "foobar");
        } catch (IOException ex) {
            Assert.fail("failed to create and write a new file: " + ex);
        }
        try {
            reindex();
        } catch (Exception ex) {
            Assert.fail("failed to reindex: " + ex);
        }
        env.maybeRefreshIndexSearchers();
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projects).executeQuery().prepareSummary();
        Assert.assertNull(searchHelper.errorMsg);
        System.out.println("multi-project search after reindex returned " +
            Integer.toString(searchHelper.totalHits) + " hits");
        Assert.assertEquals(6, searchHelper.totalHits);
        searchHelper.destroy();
        repository.removeDummyFile("c");
    }

    /**
     * Test that calling destroy() on an uninitialized instance does not
     * fail. Used to fail with a NullPointerException. See bug #19232.
     */
    @Test
    public void testDestroyUninitializedInstance() {
        new SearchHelper().destroy();
    }
}
