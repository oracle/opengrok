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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.util.TestRepository;
import static org.junit.Assert.*;

/**
 *
 * @author Trond Norbye
 */
public class IndexerTest {
    TestRepository repository;

    public IndexerTest() {
    }

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
    }

    @After
    public void tearDown() {
        repository.destroy();
    }

    /**
     * Test of doIndexerExecution method, of class Indexer.
     */
    @Test
    public void testIndexGeneration() throws Exception {
        System.out.println("Generating index by using the class methods");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty("org.opensolaris.opengrok.configuration.ctags", "ctags"));
        if (env.validateExuberantCtags()) {
            env.setSourceRoot(repository.getSourceRoot());
            env.setDataRoot(repository.getDataRoot());
            env.setVerbose(true);
            Indexer.getInstance().prepareIndexer(env, true, true, "/c", null, false, false, false, null, null);
            Indexer.getInstance().doIndexerExecution(true, 1, null, null);
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
        }
    }

    /**
     * Test of doIndexerExecution method, of class Indexer.
     */
    @Test
    public void testMain() throws IOException {
        System.out.println("Generate index by using command line options");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty("org.opensolaris.opengrok.configuration.ctags", "ctags"));
        if (env.validateExuberantCtags()) {
            String[] argv = { "-S", "-P", "-p", "/c", "-H", "-Q", "off", "-s", repository.getSourceRoot(), "-d", repository.getDataRoot(), "-v"};
            Indexer.main(argv);
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
        }
    }

    private class MyIndexChangeListener implements org.opensolaris.opengrok.index.IndexChangedListener {
        List<String> files = new ArrayList<String>();

        public void fileAdded(String path, String analyzer) {
            files.add(path);
        }

        public void fileRemoved(String path) {
            files.add(path);
        }
    }

    @Test
    public void testRFE2575() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty("org.opensolaris.opengrok.configuration.ctags", "ctags"));
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        HistoryGuru.getInstance().addRepositories(repository.getSourceRoot());

        if (env.validateExuberantCtags()) {
            Project project = new Project();
            project.setPath("/rfe2575");
            IndexDatabase idb = new IndexDatabase(project);
            assertNotNull(idb);
            MyIndexChangeListener listener = new MyIndexChangeListener();
            idb.addIndexChangedListener(listener);
            idb.update();
            assertEquals(2, listener.files.size());
            repository.purgeData();
            RuntimeEnvironment.getInstance().setIndexVersionedFilesOnly(true);
            idb = new IndexDatabase(project);
            listener = new MyIndexChangeListener();
            idb.addIndexChangedListener(listener);
            idb.update();
            assertEquals(1, listener.files.size());
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
        }
    }
}