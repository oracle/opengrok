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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.CtagsInstalled;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.MercurialRepositoryTest;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;

import javax.ws.rs.core.Application;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;

@ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
@ConditionalRun(RepositoryInstalled.GitInstalled.class)
@ConditionalRun(CtagsInstalled.class)
public class RepositoriesControllerTest extends JerseyTest {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Override
    protected Application configure() {
        return new ResourceConfig(RepositoriesController.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream("repositories.zip"));

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // This should match Configuration constructor.
        env.setProjects(new ConcurrentHashMap<>());
        env.setRepositories(new ArrayList<>());
        env.getProjectRepositoriesMap().clear();

        repository.destroy();
    }

    @Test
    public void testGetRepositoryTypeOfNonExistenRepository() throws Exception {
        assertEquals(Paths.get("/totally-nonexistent-repository").toString() + ":N/A",
                getRepoType(Paths.get("/totally-nonexistent-repository").toString()));
    }

    @Test
    public void testGetRepositoryType() throws Exception {
        // Create sub-repository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
                "clone", mercurialRoot.getAbsolutePath(),
                mercurialRoot.getAbsolutePath() + File.separator + "closed");

        env.setHistoryEnabled(true);
        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                false, // don't create dictionary
                null, // subFiles - needed when refreshing history partially
                null); // repositories - needed when refreshing history partially

        assertEquals(Paths.get("/mercurial").toString() + ":Mercurial",
                getRepoType(Paths.get("/mercurial").toString()));
        assertEquals(Paths.get("/mercurial/closed").toString() + ":Mercurial",
                getRepoType(Paths.get("/mercurial/closed").toString()));
        assertEquals(Paths.get("/git").toString() + ":git",
                getRepoType(Paths.get("/git").toString()));
    }

    private String getRepoType(final String repository) {
        return target("repositories")
                .path("type")
                .queryParam("repository", repository)
                .request()
                .get(String.class);
    }

}
