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
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Test cleanup of renamed thread pool after indexing.
 *
 * @author Vladimir Kotal
 */
public class IndexerRepoTest {

    TestRepository repository;
    private final String ctagsProperty = "org.opensolaris.opengrok.analysis.Ctags";

    @BeforeClass
    public static void setUpClass() throws Exception {
        assertTrue("No point in running indexer tests without valid ctags",
                RuntimeEnvironment.getInstance().validateExuberantCtags());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

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
            if (threads[i].getName() == null) {
                continue;
            }
            assertEquals(false, threads[i].getName().contains("renamed-handling"));
        }
    }

    @Test
    public void testMainWithH() throws IOException {
        System.out.println("Generate index by using command line options with -H");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty(ctagsProperty, "ctags"));
        if (env.validateExuberantCtags()) {
            String[] argv = {"-S", "-H", "-s", repository.getSourceRoot(),
                "-d", repository.getDataRoot(), "-v"};
            Indexer.main(argv);
            checkNumberOfThreads();
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
        }
    }

    @Test
    public void testMainWithoutH() throws IOException {
        System.out.println("Generate index by using command line options without -H");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty(ctagsProperty, "ctags"));
        if (env.validateExuberantCtags()) {
            String[] argv = {"-S", "-P", "-s", repository.getSourceRoot(),
                "-d", repository.getDataRoot(), "-v"};
            Indexer.main(argv);
            checkNumberOfThreads();
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
        }
    }
}
