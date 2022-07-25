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
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.History;
import org.opengrok.indexer.history.HistoryEntry;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.controller.HistoryController.HistoryDTO;
import org.opengrok.web.api.v1.controller.HistoryController.HistoryEntryDTO;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opengrok.web.api.v1.controller.HistoryController.getHistoryDTO;

class HistoryControllerTest extends OGKJerseyTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Override
    protected Application configure() {
        return new ResourceConfig(HistoryController.class);
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/repositories"));

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        env.setHistoryEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                // don't create dictionary
                null, // subFiles - needed when refreshing history partially
                null); // repositories - needed when refreshing history partially
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // This should match Configuration constructor.
        env.setProjects(new ConcurrentHashMap<>());
        env.setRepositories(new ArrayList<>());
        env.getProjectRepositoriesMap().clear();

        repository.destroy();
    }

    @Test
    void testHistoryDTOEquals() {
        HistoryEntry historyEntry = new HistoryEntry(
                "1",
                new Date(1245446973L / 60 * 60 * 1000),
                "xyz",
                "foo",
                true);
        HistoryEntryDTO entry1 = new HistoryEntryDTO(historyEntry);
        historyEntry.setAuthor("abc");
        HistoryEntryDTO entry2 = new HistoryEntryDTO(historyEntry);

        assertEquals(entry1, entry1);
        assertNotEquals(entry1, entry2);

        HistoryDTO history1 = new HistoryDTO(Collections.singletonList(entry1), 0, 1, 1);
        HistoryDTO history2 = new HistoryDTO(Collections.singletonList(entry2), 0, 1, 1);
        assertEquals(history1, history1);
        assertNotEquals(history1, history2);
    }

    /**
     * This test retrieves history for a directory, which means it will not go through history cache.
     */
    @Test
    void testHistoryGet() throws Exception {
        final String path = "git";
        int size = 5;
        int start = 2;
        Response response = target("history")
                .queryParam("path", path)
                .queryParam("max", size)
                .queryParam("start", start)
                .request()
                .get();
        HistoryDTO history = response.readEntity(new GenericType<>() {
        });
        assertNotNull(history);
        assertEquals(size, history.getEntries().size());
        assertEquals("Kryštof Tulinger <krystof.tulinger@oracle.com>", history.getEntries().get(0).getAuthor());

        History repoHistory = HistoryGuru.getInstance().getHistory(new File(repository.getSourceRoot(), path));
        assertEquals(history, getHistoryDTO(repoHistory.getHistoryEntries(size, start), repoHistory.getTags(),
                start, size, repoHistory.getHistoryEntries().size()));
    }
}
