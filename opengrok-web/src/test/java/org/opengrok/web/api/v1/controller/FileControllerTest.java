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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.server.ResourceConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;

import javax.ws.rs.core.Application;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;

public class FileControllerTest extends OGKJerseyTest {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Override
    protected Application configure() {
        return new ResourceConfig(FileController.class);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream("repositories.zip"));

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        env.setHistoryEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                false, // don't create dictionary
                null, // subFiles - needed when refreshing history partially
                null); // repositories - needed when refreshing history partially
        Indexer.getInstance().doIndexerExecution(true, Collections.singletonList("/git"), null);
    }

    @After
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
    public void testFileContent() throws IOException {
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
    public void testFileGenre() {
        final String path = "git/main.c";
        String genre = target("file")
                .path("genre")
                .queryParam("path", path)
                .request()
                .get(String.class);
        assertEquals("PLAIN", genre);
    }
}
