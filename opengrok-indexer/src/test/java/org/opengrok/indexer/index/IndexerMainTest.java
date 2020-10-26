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
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.util.TestRepository;

import java.io.IOException;

import static org.junit.Assert.assertFalse;

public class IndexerMainTest {
    private TestRepository repository;

    @Before
    public void setUp() throws IOException {
        repository = new TestRepository();
        // For these tests we need Mercurial repository with renamed files.
        repository.create(HistoryGuru.class.getResourceAsStream("repositories.zip"));
    }

    @After
    public void tearDown() {
        repository.destroy();
    }

    private void checkNumberOfThreads() {
        /*
         * There should not be any threads in the renamed pool now.
         * We need to check it like this since the test framework tears
         * down the threads at the end of the test case run so any
         * hangs due to executors not being shut down would not be visible.
         */
        ThreadGroup mainGroup = Thread.currentThread().getThreadGroup();
        Thread[] threads = new Thread[mainGroup.activeCount()];
        mainGroup.enumerate(threads);
        for (int i = 0; i < threads.length; i++) {
            if (threads[i] == null || threads[i].getName() == null) {
                continue;
            }
            assertFalse(threads[i].getName().contains("renamed-handling"));
        }
    }

    /**
     * Test cleanup of renamed thread pool after indexing with -H.
     */
    @Test
    public void testMainWithH() {
        System.out.println("Generate index by using command line options with -H");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String[] argv = {"-S", "-H", "-s", repository.getSourceRoot(),
                "-d", repository.getDataRoot(), "-v", "-c", env.getCtags()};
        Indexer.main(argv);
        checkNumberOfThreads();
    }

    /**
     * Test cleanup of renamed thread pool after indexing without -H.
     */
    @Test
    public void testMainWithoutH() {
        System.out.println("Generate index by using command line options without -H");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String[] argv = {"-S", "-P", "-s", repository.getSourceRoot(),
                "-d", repository.getDataRoot(), "-v", "-c", env.getCtags()};
        Indexer.main(argv);
        checkNumberOfThreads();
    }
}
