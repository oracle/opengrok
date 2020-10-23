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
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.util.List;

/**
 * Test file-based history cache with for git-octopus.
 * @author Vladimir Kotal
 */
public class FileHistoryCacheOctopusTest {

    private TestRepository repositories;
    private FileHistoryCache cache;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Before
    public void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResourceAsStream("/history/git-octopus.zip"));

        cache = new FileHistoryCache();
        cache.initialize();
    }

    @After
    public void tearDown() {
        repositories.destroy();
        repositories = null;

        cache = null;
    }

    @ConditionalRun(RepositoryInstalled.GitInstalled.class)
    @Test
    public void testStoreAndGet() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "git-octopus");
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);

        cache.store(historyToStore, repo);

        assertEquals("latest git-octopus commit", "206f862b",
                cache.getLatestCachedRevision(repo));

        History dHist = cache.get(new File(reposRoot, "d"), repo, true);
        assertNotNull("cache get() for git-octopus/d", dHist);

        List<HistoryEntry> entries = dHist.getHistoryEntries();
        String firstMessage = entries.get(0).getMessage();
        String lastMessage = entries.get(entries.size() - 1).getMessage();
        assertEquals("first message equals last", firstMessage, lastMessage);
        assertEquals("Merge branches 'file_a', 'file_b' and 'file_c' into master, and add d",
                firstMessage);
        assertEquals("git-octopus/d has one cached log", 1, entries.size());
    }
}
