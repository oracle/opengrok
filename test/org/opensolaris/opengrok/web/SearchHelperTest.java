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
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Project;
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
        env.setHistoryEnabled(false);
    }

    @After
    public void tearDown() {
        repository.destroy();
    }

    private void reindex() throws Exception {
        System.out.println("Generating index by using the class methods");

        Indexer.getInstance().prepareIndexer(env, true, true,
            new TreeSet<>(Arrays.asList(new String[]{"/c"})),
            false, false, null, null, new ArrayList<>(), false);
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
        return sh;
    }

    @Test
    public void testSearchAfterReindex() {
        SortedSet<String> projectNames = new TreeSet<>();
        SearchHelper searchHelper;

        env.setProjectsEnabled(true);

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
        projectNames.add("c");
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projectNames).executeQuery().prepareSummary();
        Assert.assertNull(searchHelper.errorMsg);
        System.out.println("single project search returned " +
            Integer.toString(searchHelper.totalHits) + " hits");
        Assert.assertEquals(4, searchHelper.totalHits);
        searchHelper.destroy();

        // Search for existing term in multiple projects.
        projectNames.add("document");
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projectNames).executeQuery().prepareSummary();
        Assert.assertNull(searchHelper.errorMsg);
        System.out.println("multi-project search returned " +
            Integer.toString(searchHelper.totalHits) + " hits");
        Assert.assertEquals(5, searchHelper.totalHits);
        searchHelper.destroy();

        // Search for non-existing term.
        searchHelper = this.getSearchHelper("CannotExistAnywhereForSure")
            .prepareExec(projectNames).executeQuery().prepareSummary();
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
            .prepareExec(projectNames).executeQuery().prepareSummary();
        Assert.assertNull(searchHelper.errorMsg);
        System.out.println("multi-project search after reindex returned " +
            Integer.toString(searchHelper.totalHits) + " hits");
        Assert.assertEquals(6, searchHelper.totalHits);
        searchHelper.destroy();
        repository.removeDummyFile("c");
    }

    @Test
    public void testPrepareExecInvalidInput() {
        SortedSet<String> projectNames = new TreeSet<>();
        SearchHelper searchHelper;

        env.setProjectsEnabled(true);

        // Fake project addition to avoid reindex.
        Project project = new Project("c", "/c");
        env.getProjects().put("c", project);
        project = new Project("java", "/java");
        project.setIndexed(true);
        env.getProjects().put("java", project);

        // Try to prepare search for project that is not yet indexed.
        projectNames.add("c");
        projectNames.add("java");
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projectNames);
        Assert.assertNotNull(searchHelper.errorMsg);
        Assert.assertTrue(searchHelper.errorMsg.contains("not indexed"));
        Assert.assertFalse(searchHelper.errorMsg.contains("java"));

        // Try to prepare search for list that contains non-existing project.
        projectNames.add("totally_nonexistent_project");
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projectNames);
        Assert.assertNotNull(searchHelper.errorMsg);
        Assert.assertTrue(searchHelper.errorMsg.contains("invalid projects"));
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
