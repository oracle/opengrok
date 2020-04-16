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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2020, Ric Harris <harrisric@users.noreply.github.com>.
 */

package org.opengrok.indexer.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.util.TestRepository;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Trond Norbye
 */
public class HugeTextTest {

    private static RuntimeEnvironment env;
    private TestRepository repository;
    private int savedHugeTextLimitCharacters;
    private int savedHugeTextThresholdBytes;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @BeforeClass
    public static void setUpClass() {
        env = RuntimeEnvironment.getInstance();
        RepositoryFactory.initializeIgnoredNames(env);
    }

    @Before
    public void setUp() throws IOException {
        repository = new TestRepository();
        repository.create(HugeTextTest.class.getResourceAsStream("source.zip"));

        savedHugeTextLimitCharacters = env.getHugeTextLimitCharacters();
        savedHugeTextThresholdBytes = env.getHugeTextThresholdBytes();
    }

    @After
    public void tearDown() {
        repository.destroy();

        env.setHugeTextLimitCharacters(savedHugeTextLimitCharacters);
        env.setHugeTextThresholdBytes(savedHugeTextThresholdBytes);
    }

    @Test
    public void shouldIndexFilesPerChangingHugeTextSettings() throws Exception {
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setRepositories(repository.getSourceRoot());

        Project project = new Project("sql");
        project.setPath("/sql");

        IndexDatabase idb = new IndexDatabase(project);
        ConcurrentIndexChangeListener listener = new ConcurrentIndexChangeListener();
        idb.addIndexChangedListener(listener);
        idb.update();
        assertEquals("should add expected files",2, listener.addedFiles.size());
        assertTrue("removedFiles should be empty", listener.removedFiles.isEmpty());
        assertTrue("should have added /sql/test.sql", listener.addedFiles.contains(
                new AddedFile("/sql/test.sql", "SQLAnalyzer")));
        assertTrue("should have added /sql/test.sql", listener.addedFiles.contains(
                new AddedFile("/sql/bug18586.sql", "SQLAnalyzer")));

        env.setHugeTextThresholdBytes(300);
        listener.reset();
        idb.update();
        assertEquals("should add expected files",1, listener.addedFiles.size());
        assertEquals("should remove expected files",1, listener.removedFiles.size());
        assertTrue("should have added /sql/test.sql", listener.addedFiles.contains(
                new AddedFile("/sql/test.sql", "HugeTextAnalyzer")));
        assertTrue("should have removed /sql/test.sql", listener.removedFiles.contains(
                "/sql/test.sql"));

        env.setHugeTextThresholdBytes(savedHugeTextThresholdBytes);
        listener.reset();
        idb.update();
        assertEquals("should add expected files",1, listener.addedFiles.size());
        assertEquals("should remove expected files",1, listener.removedFiles.size());
        assertTrue("should have added /sql/test.sql", listener.addedFiles.contains(
                new AddedFile("/sql/test.sql", "SQLAnalyzer")));
        assertTrue("should have removed /sql/test.sql", listener.removedFiles.contains(
                "/sql/test.sql"));
    }

    private static class ConcurrentIndexChangeListener implements IndexChangedListener {

        final Queue<AddedFile> addedFiles = new ConcurrentLinkedQueue<>();
        final Queue<String> removedFiles = new ConcurrentLinkedQueue<>();

        @Override
        public void fileAdd(String path, String analyzer) {
        }

        @Override
        public void fileAdded(String path, String analyzer) {
            addedFiles.add(new AddedFile(path, analyzer));
        }

        @Override
        public void fileRemove(String path) {
        }

        @Override
        public void fileRemoved(String path) {
            removedFiles.add(path);
        }

        void reset() {
            this.addedFiles.clear();
            this.removedFiles.clear();
        }
    }

    private static class AddedFile {
        final String path;
        final String analyzer;

        AddedFile(String path, String analyzer) {
            this.path = path;
            this.analyzer = analyzer;
        }

        /** Generated by IntelliJ. */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AddedFile addedFile = (AddedFile) o;

            if (!path.equals(addedFile.path)) {
                return false;
            }
            return analyzer.equals(addedFile.analyzer);
        }

        /** Generated by IntelliJ. */
        @Override
        public int hashCode() {
            int result = path.hashCode();
            result = 31 * result + analyzer.hashCode();
            return result;
        }
    }
}
