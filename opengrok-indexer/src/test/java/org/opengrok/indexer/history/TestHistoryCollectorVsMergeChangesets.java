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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test that even though merge changesets are disabled in history cache,
 * they are accounted for w.r.t. the last changeset visited.
 * This prevents duplicate documents to be created in the index on subsequent
 * indexer runs.
 * Tested only for Git.
 */
public class TestHistoryCollectorVsMergeChangesets {
    private static TestRepository repository = new TestRepository();

    @BeforeAll
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(TestHistoryCollectorVsMergeChangesets.class.getResourceAsStream(
                "/history/git-merge.zip"));
        RuntimeEnvironment.getInstance().setMergeCommitsEnabled(false);
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
        repository = null;
    }

    // TODO: test also subsequent reindex + parametrize based on env.isHistoryCachePerPartesEnabled()
    @Test
    void testHistoryCollectorVsMergeChangeset() throws Exception {
        File repositoryRoot = new File(repository.getSourceRoot(), "git-merge");
        GitRepository repo = (GitRepository) RepositoryFactory.getRepository(repositoryRoot);
        assertFalse(repo.isMergeCommitsEnabled());

        FileHistoryCache cache = new FileHistoryCache();
        cache.initialize();

        History historyToStore = repo.getHistory(repositoryRoot);
        assertNotNull(historyToStore);
        cache.store(historyToStore, repo);
        assertEquals("4d1b7cfb", cache.getLatestCachedRevision(repo));
    }
}
