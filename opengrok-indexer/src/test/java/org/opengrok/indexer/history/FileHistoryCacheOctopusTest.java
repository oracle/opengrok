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
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test file-based history cache with for git-octopus.
 * @author Vladimir Kotal
 */
public class FileHistoryCacheOctopusTest {

    private TestRepository repositories;
    private FileHistoryCache cache;

    @BeforeEach
    public void setUp() throws Exception {
        RuntimeEnvironment.getInstance().setMergeCommitsEnabled(true);

        repositories = new TestRepository();
        repositories.create(getClass().getResourceAsStream("/history/git-octopus.zip"));

        cache = new FileHistoryCache();
        cache.initialize();
    }

    @AfterEach
    public void tearDown() {
        repositories.destroy();
        repositories = null;

        cache = null;
    }

    @Test
    public void testStoreAndGet() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "git-octopus");
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);

        cache.store(historyToStore, repo);

        assertEquals("206f862b18a4e1a73025e6c0c82883cb92a89b1d", cache.getLatestCachedRevision(repo), "latest git-octopus commit");

        History dHist = cache.get(new File(reposRoot, "d"), repo, true);
        assertNotNull(dHist, "cache get() for git-octopus/d");

        List<HistoryEntry> entries = dHist.getHistoryEntries();
        String firstMessage = entries.get(0).getMessage();
        String lastMessage = entries.get(entries.size() - 1).getMessage();
        assertEquals(firstMessage, lastMessage, "first message equals last");
        assertEquals("Merge branches 'file_a', 'file_b' and 'file_c' into master, and add d",
                firstMessage);
        assertEquals(1, entries.size(), "git-octopus/d has one cached log");
    }
}
