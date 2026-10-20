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
 * Copyright (c) 2008, 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.BAZAAR;

/**
 * Simple Bazaar repository test.
 *
 * @author austvik
 */
@EnabledForRepository(BAZAAR)
class BazaarRepositoryTest {

    private BazaarRepository bazaarRepository;
    private static TestRepository repository = new TestRepository();
    private static File repositoryRoot;

    @BeforeAll
    static void setUpTestRepository() throws IOException, URISyntaxException {
        repository.create(Objects.requireNonNull(BazaarRepository.class.getResource("/repositories")));
        repositoryRoot = new File(repository.getSourceRoot(), "bazaar");
        RuntimeEnvironment.getInstance().setSourceRoot(System.getProperty("java.io.tmpdir"));
    }

    @BeforeEach
    void setUpBeforeClass() throws Exception {
        bazaarRepository = new BazaarRepository();
        bazaarRepository.setDirectoryName(repositoryRoot);
    }

    @AfterEach
    void tearDownAfterClass() throws Exception {
        bazaarRepository = null;
    }

    @AfterAll
    static void tearDown() {
        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    /**
     * Test of parse method, of class BazaarHistoryParser.
     * @throws Exception exception
     */
    @Test
    void parseEmpty() throws Exception {
        var parser = new BazaarHistoryParser(bazaarRepository);
        History result = parser.parse("");
        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(0, result.getHistoryEntries().size(), "Should not contain any history entries");
    }

    @Test
    void parseLogFile() throws Exception {
        History result = bazaarRepository.getHistory(new File(repositoryRoot, "main.c"));

        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(List.of("2", "1"), result.getHistoryEntries().stream().
                map(HistoryEntry::getRevision).toList());
        assertTrue(result.getHistoryEntries().stream().allMatch(entry -> entry.getFiles().isEmpty()));
    }

    @Test
    void parseLogWithIndentedSeparatorInMessage() throws Exception {
        String output = "------------------------------------------------------------\n" +
                "revno: 1\n" +
                "committer: username@example.com\n" +
                "timestamp: Wed 2008-10-01 10:01:34 +0200\n" +
                "message:\n" +
                "  before separator\n" +
                "    ------------------------------------------------------------\n" +
                "  after separator\n";

        var parser = new BazaarHistoryParser(bazaarRepository);
        History result = parser.parse(output);

        assertEquals(1, result.getHistoryEntries().size());
        String message = result.getHistoryEntries().getFirst().getMessage();
        assertTrue(message.contains("before separator"));
        assertTrue(message.contains("------------------------------------------------------------"));
        assertTrue(message.contains("after separator"));
    }

    @Test
    void parseLogDirectory() throws Exception {
        History result = bazaarRepository.getHistory(repositoryRoot);

        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(List.of("2", "1"), result.getHistoryEntries().stream().
                map(HistoryEntry::getRevision).toList());

        String pathPrefix = File.separator + "bazaar" + File.separator;
        assertEquals(Set.of(pathPrefix + "Makefile", pathPrefix + "main.c"),
                result.getHistoryEntries().get(0).getFiles());
        assertEquals(Set.of(pathPrefix + "Makefile", pathPrefix + "header.h", pathPrefix + "main.c"),
                result.getHistoryEntries().get(1).getFiles());
    }

    /**
     * Provides test of {@link BazaarRepository#annotate(File, String)}.
     */
    @Test
    void annotate() throws Exception {
        final String fileName = "header.h";
        Annotation result = bazaarRepository.annotate(new File(repositoryRoot, fileName), null);

        assertNotNull(result);
        assertEquals(2, result.size());
        for (int i = 1; i <= 2; i++) {
            assertTrue(result.isEnabled(i));
            assertEquals("1", result.getRevision(i));
        }
        assertEquals(fileName, result.getFilename());
    }

    /**
     * Provides test of {@link BazaarRepository#fileHasAnnotation}.
     */
    @Test
    void fileHasAnnotation() {
        boolean result = bazaarRepository.fileHasAnnotation(null);
        assertTrue(result);
    }

    /**
     * Provides test of {@link BazaarRepository#fileHasHistory}.
     */
    @Test
    void fileHasHistory() {
        boolean result = bazaarRepository.fileHasHistory(null);
        assertTrue(result);
    }
}
