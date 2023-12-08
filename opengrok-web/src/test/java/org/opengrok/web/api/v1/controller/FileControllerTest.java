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
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.core.GenericType;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.RestApp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FileControllerTest extends OGKJerseyTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Override
    protected DeploymentContext configureDeployment() {
        return ServletDeploymentContext.forServlet(new ServletContainer(new RestApp())).build();
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
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
        Indexer.getInstance().doIndexerExecution(Collections.singletonList("/git"), null);
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
    void testFileContent() throws IOException {
        final String path = "git/header.h";
        byte[] encoded = Files.readAllBytes(Paths.get(repository.getSourceRoot(), path));
        String contents = new String(encoded);
        String output = target("file")
                .path("content")
                .queryParam("path", path)
                .request()
                .get(String.class);
        assertEquals(contents, output);
    }

    @Test
    void testFileGenre() {
        final String path = "git/main.c";
        String genre = target("file")
                .path("genre")
                .queryParam("path", path)
                .request()
                .get(String.class);
        assertEquals("PLAIN", genre);
    }

    @Test
    void testFileDefinitions() {
        final String path = "git/main.c";
        GenericType<List<Definitions.Tag>> type = new GenericType<>() {
        };
        List<Definitions.Tag> defs = target("file")
                .path("defs")
                .queryParam("path", path)
                .request()
                .get(type);
        assertFalse(defs.isEmpty());
        assertAll(() -> assertFalse(defs.stream().map(Definitions.Tag::getType).anyMatch(Objects::isNull)),
                () -> assertFalse(defs.stream().map(Definitions.Tag::getSymbol).anyMatch(Objects::isNull)),
                () -> assertFalse(defs.stream().map(Definitions.Tag::getText).anyMatch(Objects::isNull)),
                () -> assertFalse(defs.stream().filter(e -> !e.getType().equals("local")).
                        map(Definitions.Tag::getSignature).anyMatch(Objects::isNull)),
                () -> assertFalse(defs.stream().map(Definitions.Tag::getLine).anyMatch(e -> e <= 0)),
                () -> assertFalse(defs.stream().map(Definitions.Tag::getLineStart).anyMatch(e -> e <= 0)),
                () -> assertFalse(defs.stream().map(Definitions.Tag::getLineEnd).anyMatch(e -> e <= 0)));
    }
}
