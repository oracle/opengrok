package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.controller.FileController.LineDTO;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;

public class FileControllerTest extends JerseyTest {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Override
    protected Application configure() {
        return new ResourceConfig(FileController.class);
    }

    @Before
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
        Indexer.getInstance().doIndexerExecution(true, null, null);
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
    public void testFileContent() {
        final String path = "git/header.h";
        List<LineDTO> list = target("file")
                .path("content")
                .queryParam("path", path)
                .request()
                .get(new GenericType<List<LineDTO>>() {});
        assertEquals(2, list.size());
        assertEquals("#include <stdlib.h>", list.get(0).getLine());
        assertEquals(1, list.get(0).getNumber());
        assertEquals(2, list.get(1).getNumber());
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
