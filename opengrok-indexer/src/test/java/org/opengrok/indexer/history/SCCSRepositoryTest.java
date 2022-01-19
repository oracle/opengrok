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

import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.SCCS;

/**
 * Test {@link SCCSRepository}.
 * The {@code #testDetermineParent*} tests should not run in parallel, otherwise it should be okay.
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
        URL repositoriesUrl = SCCSRepositoryTest.class.getResource("/repositories");
        assertNotNull(repositoriesUrl);
        repository.create(repositoriesUrl);

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
        DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
        List<HistoryEntry> entries = List.of(
                new HistoryEntry("1.2", dateFormat.parse("Tue Aug 12 22:11:54 2008"),
                        "trond", "Fixed lint warnings\n", true),
                new HistoryEntry("1.1", dateFormat.parse("Tue Aug 12 22:09:23 2008"),
                        "trond", "date and time created 08/08/12 22:09:23 by trond\n\n", true));
        History expectedHistory = new History(entries);
        assertEquals(expectedHistory, history);
    }

    /**
     * Negative test of {@link SCCSRepository#determineParent(CommandTimeoutType)}.
     */
    @Test
    void testDetermineParentInvalid() throws Exception {
        File codemgrDir = new File(repositoryRoot, SCCSRepository.CODEMGR_WSDATA);
        if (!codemgrDir.isDirectory()) {
            assertTrue(codemgrDir.mkdirs());
        }
        File parentFile = new File(codemgrDir, "parent");
        assertTrue(parentFile.createNewFile());
        try (PrintWriter out = new PrintWriter(parentFile.toString())) {
            out.println("foo");
        }
        assertNull(sccsRepository.determineParent(CommandTimeoutType.INDEXER));
        assertTrue(parentFile.delete());
    }

    /**
     * Test of {@link SCCSRepository#determineParent(CommandTimeoutType)}.
     */
    @Test
    void testDetermineParent() throws Exception {
        File codemgrDir = new File(repositoryRoot, SCCSRepository.CODEMGR_WSDATA);
        if (!codemgrDir.isDirectory()) {
            assertTrue(codemgrDir.mkdirs());
        }
        File parentFile = new File(codemgrDir, "parent");
        assertTrue(parentFile.createNewFile());
        final String expectedParent = "/foo";
        try (PrintWriter out = new PrintWriter(parentFile.toString())) {
            out.println("VERSION 1");
            out.println(expectedParent);
        }
        assertEquals(expectedParent, sccsRepository.determineParent(CommandTimeoutType.INDEXER));
        assertTrue(parentFile.delete());
    }

    private static class GetHistoryTestParams {
        private final String revision;
        private final boolean shouldContain;

        GetHistoryTestParams(String revision, boolean shouldContain) {
            this.revision = revision;
            this.shouldContain = shouldContain;
        }
    }

    private static List<GetHistoryTestParams> getHistoryGetParams() {
        return List.of(new GetHistoryTestParams("1.1", false),
                new GetHistoryTestParams("1.2", true));
    }

    /**
     * Test of {@link SCCSRepository#getHistoryGet(OutputStream, String, String, String)}.
     */
    @ParameterizedTest
    @MethodSource("getHistoryGetParams")
    void testGetHistoryGet(final GetHistoryTestParams testParams) throws Exception {
        try (InputStream inputStream = sccsRepository.getHistoryGet(repositoryRoot.toString(),
                "main.c", testParams.revision)) {
            assertNotNull(inputStream);
            byte[] buffer = new byte[1024];
            IOUtils.readFully(inputStream, buffer);
            String fileContents = new String(buffer);
            final String castedPrintf = "(void)printf";
            if (testParams.shouldContain) {
                assertTrue(fileContents.contains(castedPrintf));
            } else {
                assertFalse(fileContents.contains(castedPrintf));
            }
        }
    }
}
