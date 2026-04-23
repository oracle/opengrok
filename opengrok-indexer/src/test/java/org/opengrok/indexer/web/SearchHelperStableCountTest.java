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
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SearchHelper} produces an exact and stable {@code totalHits}
 * regardless of the {@code maxItems} window, so the UI search total matches the REST
 * API and does not vary across repeated searches. With the previous implementation
 * {@code searcher.search(query, n, sort)} uses a {@code totalHitsThreshold} of 1000,
 * so for queries matching more than 1000 documents {@code totalHits} becomes an
 * approximate lower bound and can drift between calls.
 *
 * <p>To reliably exercise that threshold the test creates a synthetic corpus of
 * {@value #SYNTHETIC_DOC_COUNT} files, each containing a unique marker token.</p>
 */
class SearchHelperStableCountTest {

    private static final String MARKER = "searchhelperbug3239marker";
    private static final int SYNTHETIC_DOC_COUNT = 1500;
    private static final String SYNTHETIC_PROJECT = "synthetic_bulk";

    private static TestRepository repository;
    private static RuntimeEnvironment env;

    @BeforeAll
    static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(SearchHelperStableCountTest.class.getClassLoader().getResource("sources"));

        Path syntheticDir = Path.of(repository.getSourceRoot(), SYNTHETIC_PROJECT);
        Files.createDirectories(syntheticDir);
        // Produce strong score variance so Lucene's block-max WAND can skip low-scoring
        // docs once the heap is full. With the pre-fix implementation that lets the
        // totalHitsThreshold of 1000 turn the totalHits into a lower-bound estimate.
        for (int i = 0; i < SYNTHETIC_DOC_COUNT; i++) {
            int repeat = (i == 0) ? 10_000 : 1;
            Files.writeString(syntheticDir.resolve("doc_" + i + ".txt"),
                    (MARKER + " ").repeat(repeat) + "\n");
        }

        env = RuntimeEnvironment.getInstance();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(false);
        env.setProjectsEnabled(true);

        Indexer.getInstance().prepareIndexer(env, true, true, null, null);
        env.setDefaultProjectsFromNames(
                new TreeSet<>(Collections.singletonList("/" + SYNTHETIC_PROJECT)));
        Indexer.getInstance().doIndexerExecution(null, null);
    }

    @AfterAll
    static void tearDownClass() {
        repository.destroy();
    }

    @Test
    void testTotalHitsIsExactAndStable() {
        SortedSet<String> projectNames = new TreeSet<>();
        projectNames.add(SYNTHETIC_PROJECT);

        // Run with a large maxItems first to capture the true matching document count;
        // the pre-fix implementation returned an exact count only when the heap was
        // large enough to disable block-max WAND early termination.
        long reference = runSearchForTotalHits(projectNames, SYNTHETIC_DOC_COUNT);
        assertTrue(reference > 1000,
                "need more than the Lucene default threshold of 1000 matches to trigger the bug, got "
                        + reference);

        for (int maxItems : new int[]{1, 2, 10, 100}) {
            assertEquals(reference, runSearchForTotalHits(projectNames, maxItems),
                    "totalHits must be exact and stable regardless of maxItems");
        }
    }

    @Test
    void testTotalHitsIsStableAcrossRepeatedCalls() {
        SortedSet<String> projectNames = new TreeSet<>();
        projectNames.add(SYNTHETIC_PROJECT);

        long first = runSearchForTotalHits(projectNames, 1);
        assertTrue(first > 1000,
                "need more than 1000 matches to trigger the bug, got " + first);
        for (int i = 0; i < 5; i++) {
            assertEquals(first, runSearchForTotalHits(projectNames, 1),
                    "totalHits must not drift across repeated queries");
        }
    }

    private long runSearchForTotalHits(SortedSet<String> projectNames, int maxItems) {
        SearchHelper searchHelper = new SearchHelper.Builder(env.getDataRootFile(),
                env.getSourceRootFile(), null,
                new QueryBuilder().setFreetext(MARKER), env.getUrlPrefix())
                .maxItems(maxItems)
                .build()
                .prepareExec(projectNames)
                .executeQuery();
        try {
            assertNull(searchHelper.getErrorMsg());
            return searchHelper.getTotalHits();
        } finally {
            searchHelper.destroy();
        }
    }
}
