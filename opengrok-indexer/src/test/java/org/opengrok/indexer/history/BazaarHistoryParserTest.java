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
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
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
 * @author austvik
 */
class BazaarHistoryParserTest {

    private BazaarHistoryParser instance;

    private TestRepository repository;
    private BazaarRepository bzrRepo = new BazaarRepository();

    private File setUpTestRepository() throws IOException, URISyntaxException {
        repository = new TestRepository();
        repository.create(getClass().getResource("/repositories"));
        File repositoryRoot = new File(repository.getSourceRoot(), "bazaar");
        bzrRepo.setDirectoryName(repositoryRoot);
        return repositoryRoot;
    }

    @BeforeEach
    void setUp() {
        RuntimeEnvironment.getInstance().setSourceRoot(System.getProperty("java.io.tmpdir"));
        bzrRepo.setDirectoryNameRelative("bzrRepo");
        instance = new BazaarHistoryParser(bzrRepo);
    }

    @AfterEach
    void tearDown() {
        instance = null;

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
        History result = instance.parse("");
        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(0, result.getHistoryEntries().size(), "Should not contain any history entries");
    }

    @EnabledForRepository(BAZAAR)
    @Test
    void parseLogFile() throws Exception {
        File repositoryRoot = setUpTestRepository();
        History result = bzrRepo.getHistory(new File(repositoryRoot, "main.c"));

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

        History result = instance.parse(output);

        assertEquals(1, result.getHistoryEntries().size());
        String message = result.getHistoryEntries().getFirst().getMessage();
        assertTrue(message.contains("before separator"));
        assertTrue(message.contains("------------------------------------------------------------"));
        assertTrue(message.contains("after separator"));
    }

    @EnabledForRepository(BAZAAR)
    @Test
    void parseLogDirectory() throws Exception {
        File repositoryRoot = setUpTestRepository();
        History result = bzrRepo.getHistory(repositoryRoot);

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
}
