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
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.SCCS;

/**
 *
 * @author Lubos Kosco
 */
@EnabledForRepository(SCCS)
class SCCSRepositoryTest {

    private static TestRepository repository;
    private static SCCSRepository sccsRepository;
    private static File repositoryRoot;

    @BeforeAll
    public static void setup() throws Exception {
        repository = new TestRepository();
        repository.create(SCCSRepositoryTest.class.getResource("/repositories"));

        repositoryRoot = new File(repository.getSourceRoot(), "teamware");
        assertTrue(repositoryRoot.isDirectory());
        sccsRepository = (SCCSRepository) RepositoryFactory.getRepository(repositoryRoot);
        assertNotNull(sccsRepository);
    }

    @AfterAll
    public static void tearDown() {
        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    /**
     * Test of {@link SCCSRepository#isRepositoryFor(File)}.
     */
    private void testIsRepositoryFor(final String fileName, boolean shouldPass) throws IOException {
        File tdir = Files.createTempDirectory("SCCSrepotest" + fileName).toFile();
        File test = new File(tdir, fileName);
        assertTrue(test.mkdirs(), "failed to create directory");
        tdir.deleteOnExit();
        test.deleteOnExit();
        SCCSRepository instance = new SCCSRepository();
        assertEquals(shouldPass, instance.isRepositoryFor(tdir));
    }

    @Test
    void testIsRepositoryForCodemgr1() throws IOException {
        testIsRepositoryFor("Codemgr_wsdata", true);
    }

    @Test
    void testIsRepositoryForCodemgr2() throws IOException {
        testIsRepositoryFor("codemgr_wsdata", true);
    }

    @Test
    void testIsRepositoryForCodemgr3() throws IOException {
        testIsRepositoryFor("SCCS", true);
    }

    @Test
    void testIsRepositoryForCodemgrNot() throws IOException {
        testIsRepositoryFor("NOT", false);
    }

    /**
     * Test of {@link SCCSRepository#annotate(File, String)}.
     */
    @Test
    void testAnnotation() throws Exception {
        File file = new File(repositoryRoot, "main.c");
        assertTrue(file.isFile());
        Annotation annotation = sccsRepository.annotate(file, null);
        assertNotNull(annotation);
        Set<String> revSet = Set.of("1.2", "1.1");
        assertEquals(revSet, annotation.getRevisions());
    }

    @Test
    void testHasHistoryForDirectories() {
        assertFalse(sccsRepository.hasHistoryForDirectories());
    }

    /**
     * Test of {@link SCCSRepository#getHistory(File)}.
     */
    @Test
    void testGetHistory() throws Exception {
        File file = new File(repositoryRoot, "main.c");
        assertTrue(file.isFile());
        History history = sccsRepository.getHistory(file);
        assertNotNull(history);
        List<HistoryEntry> entries = List.of(
                new HistoryEntry("1.2", new Date(1218492000000L),
                        "trond", "Fixed lint warnings\n", true),
                new HistoryEntry("1.1", new Date(1218492000000L),
                        "trond", "date and time created 08/08/12 22:09:23 by trond\n\n", true));
        History expectedHistory = new History(entries);
        assertEquals(expectedHistory, history);
    }
}
