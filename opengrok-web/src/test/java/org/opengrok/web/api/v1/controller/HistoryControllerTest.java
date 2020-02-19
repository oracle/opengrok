package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.History;
import org.opengrok.indexer.history.HistoryEntry;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.controller.HistoryController.HistoryDTO;
import org.opengrok.web.api.v1.controller.HistoryController.HistoryEntryDTO;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.opengrok.web.api.v1.controller.HistoryController.getHistoryDTO;

@ConditionalRun(RepositoryInstalled.GitInstalled.class)
public class HistoryControllerTest extends JerseyTest {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Override
    protected Application configure() {
        return new ResourceConfig(HistoryController.class);
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
    public void testHistoryDTOEquals() {
        HistoryEntry historyEntry = new HistoryEntry(
                "1",
                new Date(1245446973L / 60 * 60 * 1000),
                "xyz",
                null,
                "foo",
                true);
        HistoryEntryDTO entry1 = new HistoryEntryDTO(historyEntry);
        historyEntry.setAuthor("abc");
        HistoryEntryDTO entry2 = new HistoryEntryDTO(historyEntry);

        assertEquals(entry1, entry1);
        assertNotEquals(entry1, entry2);

        HistoryDTO history1 = new HistoryDTO(Collections.singletonList(entry1));
        HistoryDTO history2 = new HistoryDTO(Collections.singletonList(entry2));
        assertEquals(history1, history1);
        assertNotEquals(history1, history2);
    }

    @Test
    public void testHistoryGet() throws Exception {
        final String path = "git";
        Response response = target("history")
                .queryParam("path", path)
                .request()
                .get();
        HistoryDTO history = response.readEntity(new GenericType<HistoryDTO>() {});
        History repoHistory = HistoryGuru.getInstance().getHistory(new File(repository.getSourceRoot(), path));
        assertEquals(history, getHistoryDTO(repoHistory.getHistoryEntries()));
    }
}
