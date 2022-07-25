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
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.index.IndexerTest;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@code SearchHelper} class.
 */
class SearchHelperTest {

    TestRepository repository;
    RuntimeEnvironment env;

    @BeforeEach
    public void setUp() throws Exception {
        repository = new TestRepository();
        repository.create(IndexerTest.class.getClassLoader().getResource("sources"));

        env = RuntimeEnvironment.getInstance();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(false);
    }

    @AfterEach
    public void tearDown() {
        repository.destroy();
    }

    private void reindex() throws Exception {
        System.out.println("Generating index by using the class methods");

        Indexer.getInstance().prepareIndexer(env, true, true,
                null, null);
        env.setDefaultProjectsFromNames(new TreeSet<>(Collections.singletonList("/c")));
        Indexer.getInstance().doIndexerExecution(null, null);
    }

    private SearchHelper getSearchHelper(String searchTerm) {
        SearchHelper sh = new SearchHelper(0, SortOrder.RELEVANCY,
                env.getDataRootFile(), env.getSourceRootFile(),
                env.getHitsPerPage(), null,
                new QueryBuilder().setFreetext(searchTerm), false,
                env.getUrlPrefix(), false, false);

        assertNotSame(0, sh.getBuilder().getSize());
        return sh;
    }

    private SearchHelper getSearchHelperPath(String searchTerm) {
        SearchHelper sh = new SearchHelper(0, SortOrder.RELEVANCY,
                env.getDataRootFile(), env.getSourceRootFile(),
                env.getHitsPerPage(), null,
                new QueryBuilder().setPath(searchTerm), false,
                env.getUrlPrefix(), false, false);

        assertNotSame(0, sh.getBuilder().getSize());
        return sh;
    }

    @Test
    void testSearchAfterReindex() throws Exception {
        SortedSet<String> projectNames = new TreeSet<>();

        env.setProjectsEnabled(true);

        reindex();

        // Search for existing term in single project.
        projectNames.add("c");
        SearchHelper searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projectNames).executeQuery().prepareSummary();
        assertNull(searchHelper.getErrorMsg());
        System.out.println("single project search returned " + searchHelper.getTotalHits() + " hits");
        assertEquals(4, searchHelper.getTotalHits());
        searchHelper.destroy();

        // Search for existing term in multiple projects.
        projectNames.add("document");
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projectNames).executeQuery().prepareSummary();
        assertNull(searchHelper.getErrorMsg());
        System.out.println("multi-project search returned " + searchHelper.getTotalHits() + " hits");
        assertEquals(5, searchHelper.getTotalHits());
        searchHelper.destroy();

        // Search for non-existing term.
        searchHelper = this.getSearchHelper("CannotExistAnywhereForSure")
            .prepareExec(projectNames).executeQuery().prepareSummary();
        assertNull(searchHelper.getErrorMsg());
        long totalHits = searchHelper.getTotalHits();
        System.out.println("multi-project search for non-existing term returned " + totalHits + " hits");
        assertEquals(0, totalHits);
        searchHelper.destroy();

        // Add a change to the repository, reindex, try to reopen the indexes
        // and repeat the search.
        repository.addDummyFile("c", "foobar");

        reindex();

        env.maybeRefreshIndexSearchers();
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projectNames).executeQuery().prepareSummary();
        assertNull(searchHelper.getErrorMsg());
        totalHits = searchHelper.getTotalHits();
        System.out.println("multi-project search after reindex returned " + totalHits + " hits");
        assertEquals(6, totalHits);
        searchHelper.destroy();
        repository.removeDummyFile("c");

        // Search for case insensitive path.
        projectNames.add("java");
        searchHelper = this.getSearchHelperPath("JaVa")
            .prepareExec(projectNames).executeQuery().prepareSummary();
        assertNull(searchHelper.getErrorMsg());
        totalHits = searchHelper.getTotalHits();
        System.out.println("multi-project search for non-existing term returned " + totalHits + " hits");
        assertEquals(5, totalHits);
        searchHelper.destroy();
    }

    @Test
    void testPrepareExecInvalidInput() {
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
        assertNotNull(searchHelper.getErrorMsg());
        assertTrue(searchHelper.getErrorMsg().contains("not indexed"));
        assertFalse(searchHelper.getErrorMsg().contains("java"));

        // Try to prepare search for list that contains non-existing project.
        projectNames.add("totally_nonexistent_project");
        searchHelper = this.getSearchHelper("foobar")
            .prepareExec(projectNames);
        assertNotNull(searchHelper.getErrorMsg());
        assertTrue(searchHelper.getErrorMsg().contains("invalid projects"));
    }
}
