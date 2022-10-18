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
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.MercurialRepositoryTest;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.index.IndexDatabase;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.index.IndexerException;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.ApiUtils;
import org.opengrok.web.api.ApiTaskManager;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.MERCURIAL;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.SUBVERSION;
import static org.opengrok.indexer.util.IOUtils.removeRecursive;

@ExtendWith(MockitoExtension.class)
@EnabledForRepository({MERCURIAL, SUBVERSION})
class ProjectsControllerTest extends OGKJerseyTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Mock
    private SuggesterService suggesterService;

    @BeforeAll
    static void setup() {
        ApiTaskManager.getInstance().addPool("projects", 1);
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
    }

    @Override
    protected DeploymentContext configureDeployment() {
        return ServletDeploymentContext.forServlet(new ServletContainer(
                new ResourceConfig(ProjectsController.class, StatusController.class)
                .register(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(suggesterService).to(SuggesterService.class);
                    }
                }))).build();
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
        env.setAnnotationCacheEnabled(true);
        env.setHandleHistoryOfRenamedFiles(true);
        RepositoryFactory.initializeIgnoredNames(env);
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
    void testAddInherit() throws Exception {
        assertTrue(env.getRepositories().isEmpty());
        assertTrue(env.getProjects().isEmpty());
        assertTrue(env.isHandleHistoryOfRenamedFiles());

        addProject("git");

        assertTrue(env.getProjects().containsKey("git"));
        assertEquals(1, env.getProjects().size());

        Project proj = env.getProjects().get("git");
        assertNotNull(proj);
        assertTrue(proj.isHandleRenamedFiles());
    }

    private Response addProject(final String project) throws InterruptedException {
        Response response = target("projects")
                .request()
                .post(Entity.text(project));
        return org.opengrok.indexer.web.ApiUtils.waitForAsyncApi(response);
    }

    /**
     * Verify that added project correctly inherits a property
     * from configuration. Ideally, this should test all properties of Project.
     */
    @Test
    void testAdd() throws Exception {
        assertTrue(env.getRepositories().isEmpty());
        assertTrue(env.getProjects().isEmpty());

        // Add a group matching the project to be added.
        String groupName = "mercurialgroup";
        Group group = new Group(groupName, "mercurial.*");
        env.getGroups().add(group);
        assertTrue(env.hasGroups());
        assertEquals(1, env.getGroups().stream().
                filter(g -> g.getName().equals(groupName)).
                collect(Collectors.toSet()).size());
        assertEquals(0, group.getRepositories().size());
        assertEquals(0, group.getProjects().size());

        // Add a sub-repository.
        String repoPath = repository.getSourceRoot() + File.separator + "mercurial";
        File mercurialRoot = new File(repoPath);
        File subDir = new File(mercurialRoot, "usr");
        assertTrue(subDir.mkdir());
        String subRepoPath = repoPath + File.separator + "usr" + File.separator + "closed";
        File mercurialSubRoot = new File(subRepoPath);
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
                "clone", mercurialRoot.getAbsolutePath(), subRepoPath);

        // Add the project.
        env.setScanningDepth(3);

        addProject("mercurial");

        // Check that the project was added properly.
        assertTrue(env.getProjects().containsKey("mercurial"));
        assertEquals(1, env.getProjects().size());
        assertEquals(2, env.getRepositories().size());
        assertEquals(1, group.getRepositories().size());
        assertEquals(0, group.getProjects().size());
        assertEquals(1, group.getRepositories().stream().
                filter(p -> p.getName().equals("mercurial")).
                collect(Collectors.toSet()).size());

        // Check that HistoryGuru now includes the project in its list.
        Set<String> directoryNames = HistoryGuru.getInstance().
                getRepositories().stream().map(RepositoryInfo::getDirectoryName).
                collect(Collectors.toSet());
        assertTrue(directoryNames.contains(repoPath) || directoryNames.contains(
                mercurialRoot.getCanonicalPath()), "though it should contain the top root,");
        assertTrue(directoryNames.contains(subRepoPath) || directoryNames.contains(
                mercurialSubRoot.getCanonicalPath()), "though it should contain the sub-root,");

        // Add more projects and check that they have been added incrementally.
        // At the same time, it checks that multiple projects can be added
        // with single message.

        addProject("git");

        assertEquals(2, env.getProjects().size());
        assertEquals(3, env.getRepositories().size());
        assertTrue(env.getProjects().containsKey("git"));

        assertFalse(HistoryGuru.getInstance().getRepositories().stream().
                map(RepositoryInfo::getDirectoryName).collect(Collectors.toSet()).
                contains("git"));
    }

    /**
     * Test that if the add is applied on already existing project,
     * the repository list is refreshed.
     */
    @Test
    void testRepositoryRefresh() throws Exception {
        addProject("mercurial");

        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
                "clone", mercurialRoot.getAbsolutePath(),
                mercurialRoot.getAbsolutePath() + File.separator + "closed");

        addProject("mercurial");

        assertEquals(2, env.getRepositories().size());
        assertEquals(2, env.getProjectRepositoriesMap().get(Project.getProject(mercurialRoot)).size());

        // Delete the newly added repository to verify it will be removed from
        // configuration after the message is reapplied. This is necessary anyway
        // for proper per-test cleanup.
        removeRecursive(new File(mercurialRoot.getAbsolutePath() + File.separator + "closed").toPath());

        addProject("mercurial");

        assertEquals(1, env.getRepositories().size());
        assertEquals(1, env.getProjectRepositoriesMap().get(Project.getProject(mercurialRoot)).size());
    }

    /**
     * This test needs to perform indexing so that it can be verified that
     * delete handling does remove the index data.
     */
    @Test
    void testDelete() throws Exception {
        String[] projectsToDelete = {"git"};

        // Add a group matching the project to be added.
        String groupName = "gitgroup";
        Group group = new Group(groupName, "git.*");
        env.getGroups().add(group);
        assertTrue(env.hasGroups());
        assertEquals(1, env.getGroups().stream().
                filter(g -> g.getName().equals(groupName)).
                collect(Collectors.toSet()).size());
        assertEquals(0, group.getRepositories().size());
        assertEquals(0, group.getProjects().size());

        assertEquals(0, env.getProjects().size());
        assertEquals(0, env.getRepositories().size());
        assertEquals(0, env.getProjectRepositoriesMap().size());

        addProject("mercurial");
        addProject("git");

        assertEquals(2, env.getProjects().size());
        assertEquals(2, env.getRepositories().size());
        assertEquals(2, env.getProjectRepositoriesMap().size());

        // Check the group was populated properly.
        assertEquals(1, group.getRepositories().size());
        assertEquals(0, group.getProjects().size());
        assertEquals(1, group.getRepositories().stream().
                filter(p -> p.getName().equals("git")).
                collect(Collectors.toSet()).size());

        // Run the indexer so that data directory is populated.
        ArrayList<String> subFiles = new ArrayList<>();
        subFiles.add("/git");
        subFiles.add("/mercurial");
        ArrayList<String> repos = new ArrayList<>();
        repos.add("/git");
        repos.add("/mercurial");
        // This is necessary so that repositories in HistoryGuru get populated.
        // For per project reindex this is called from setConfiguration() because
        // of the -R option is present.
        HistoryGuru.getInstance().invalidateRepositories(
                env.getRepositories(), null, CommandTimeoutType.INDEXER);
        env.setHistoryEnabled(true);
        Indexer.getInstance().prepareIndexer(
                env,
                false, // don't search for repositories
                false, // don't scan and add projects
                // don't create dictionary
                subFiles, // subFiles - needed when refreshing history partially
                repos); // repositories - needed when refreshing history partially
        Indexer.getInstance().doIndexerExecution(null, null);

        for (String proj : projectsToDelete) {
            deleteProject(proj);
        }

        assertEquals(1, env.getProjects().size());
        assertEquals(1, env.getRepositories().size());
        assertEquals(1, env.getProjectRepositoriesMap().size());

        // Test data removal.
        for (String projectName : projectsToDelete) {
            for (String dirName : new String[] {"historycache",
                    IndexDatabase.XREF_DIR, IndexDatabase.INDEX_DIR}) {
                File dir = new File(env.getDataRootFile(),
                        dirName + File.separator + projectName);
                assertFalse(dir.exists());
            }
        }

        // Check that HistoryGuru no longer maintains the removed projects.
        for (String p : projectsToDelete) {
            assertFalse(HistoryGuru.getInstance().getRepositories().stream().
                    map(RepositoryInfo::getDirectoryName).collect(Collectors.toSet()).
                    contains(repository.getSourceRoot() + File.separator + p));
        }

        // Check the group no longer contains the removed project.
        assertEquals(0, group.getRepositories().size());
        assertEquals(0, group.getProjects().size());
    }

    private Response deleteProject(final String project) throws InterruptedException {
        Response response = target("projects")
                .path(project)
                .request()
                .delete();
        return ApiUtils.waitForAsyncApi(response);
    }

    /**
     * This test assumes that annotation cache is enabled.
     * @param historyCache whether to use history or annotation cache
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testDeleteCache(boolean historyCache) throws Exception {
        final String cacheName = historyCache ? "historycache" : "annotationcache";
        final String projectName = "git";
        addProject(projectName);

        Indexer.getInstance().prepareIndexer(
                env,
                false, // don't search for repositories
                true, // add projects
                // don't create dictionary
                new ArrayList<>(), // subFiles - needed when refreshing history partially
                new ArrayList<>()); // repositories - needed when refreshing history partially
        Indexer.getInstance().doIndexerExecution(null, null);

        // Check the history cache is present. Assumes single file is enough.
        HistoryGuru historyGuru = HistoryGuru.getInstance();
        File file = Paths.get(env.getSourceRootPath(), projectName, "main.c").toFile();
        assertTrue(file.exists());
        if (historyCache) {
            assertTrue(historyGuru.hasHistoryCacheForFile(file));
        } else {
            assertTrue(historyGuru.hasAnnotationCacheForFile(file));
        }

        // Delete the cache via API request.
        Response initialResponse = target("projects")
                .path(projectName)
                .path(cacheName)
                .request()
                .delete();
        Response response = org.opengrok.indexer.web.ApiUtils.waitForAsyncApi(initialResponse);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Object entity = response.getEntity();
        List<String> list = response.readEntity(new GenericType<>() {
        });
        assertNotNull(entity);
        assertEquals(1, list.size());
        assertEquals("/git", list.get(0));

        // Check the cache was indeed removed. Assumes single file is enough.
        if (historyCache) {
            assertFalse(historyGuru.hasHistoryCacheForFile(file));
        } else {
            assertFalse(historyGuru.hasAnnotationCacheForFile(file));
        }

        // Check that the project and its index is still present.
        assertNotNull(env.getProjects().get(projectName));
        env.maybeRefreshIndexSearchers();
        assertNotNull(IndexDatabase.getDocument(file));
    }

    @Test
    void testIndexed() throws Exception {
        String projectName = "mercurial";

        // When a project is added, it should be marked as not indexed.
        addProject(projectName);

        assertFalse(env.getProjects().get(projectName).isIndexed());

        // Get repository info for the project.
        Project project = env.getProjects().get(projectName);
        assertNotNull(project);
        List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
        assertNotNull(riList);
        assertEquals(1, riList.size(), "there should be just 1 repository");
        RepositoryInfo ri = riList.get(0);
        assertNotNull(ri);
        assertTrue(ri.getCurrentVersion().contains("8b340409b3a8"));

        // Add some changes to the repository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");

        // copy file from jar to a temp file
        Path temp = Files.createTempFile("opengrok", "temp");
        Files.copy(HistoryGuru.getInstance().getClass().getResourceAsStream("/history/hg-export-subdir.txt"),
                temp, StandardCopyOption.REPLACE_EXISTING);

        // prevent 'uncommitted changes' error
        MercurialRepositoryTest.runHgCommand(mercurialRoot, "revert", "--all");

        MercurialRepositoryTest.runHgCommand(mercurialRoot, "import", temp.toString());

        assertTrue(temp.toFile().delete());

        // Test that the project's indexed flag becomes true only after
        // the message is applied.


        assertEquals(markIndexed(projectName).getStatusInfo().getFamily(), Response.Status.Family.SUCCESSFUL);
        assertTrue(env.getProjects().get(projectName).isIndexed(), "indexed flag should be set to true");

        // Test that the "indexed" message triggers refresh of current version
        // info in related repositories.
        riList = env.getProjectRepositoriesMap().get(project);
        assertNotNull(riList);
        ri = riList.get(0);
        assertNotNull(ri);
        assertTrue(ri.getCurrentVersion().contains("c78fa757c524"), "current version should be refreshed");
    }

    private Response markIndexed(final String project) throws InterruptedException {
        Response response = target("projects")
                .path(project)
                .path("indexed")
                .request()
                .put(Entity.text(""));
        return ApiUtils.waitForAsyncApi(response);
    }

    @Test
    void testList() throws Exception {
        addProject("mercurial");
        assertEquals(markIndexed("mercurial").getStatusInfo().getFamily(), Response.Status.Family.SUCCESSFUL);

        // Add another project.
        addProject("git");

        GenericType<List<String>> type = new GenericType<>() {
        };

        List<String> projects = target("projects")
                .request()
                .get(type);

        assertTrue(projects.contains("mercurial"));
        assertTrue(projects.contains("git"));

        List<String> indexed = target("projects")
                .path("indexed")
                .request()
                .get(type);

        assertTrue(indexed.contains("mercurial"));
        assertFalse(indexed.contains("git"));
    }

    @Test
    void testGetReposForNonExistentProject() {
        GenericType<List<String>> type = new GenericType<>() {
        };

        // Try to get repos for non-existent project first.
        List<String> repos = target("projects")
                .path("totally-nonexistent-project")
                .path("repositories")
                .request()
                .get(type);

        assertTrue(repos.isEmpty());
    }

    @Test
    void testGetRepos() throws Exception {
        GenericType<List<String>> type = new GenericType<>() {
        };

        // Create subrepository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
                "clone", mercurialRoot.getAbsolutePath(),
                mercurialRoot.getAbsolutePath() + File.separator + "closed");

        addProject("mercurial");

        // Get repositories of the project.
        List<String> repos = target("projects")
                .path("mercurial")
                .path("repositories")
                .request()
                .get(type);

        // Perform cleanup of the subrepository in order not to interfere
        // with other tests.
        removeRecursive(new File(mercurialRoot.getAbsolutePath() +
                File.separator + "closed").toPath());

        // test
        assertEquals(
                new ArrayList<>(Arrays.asList(Paths.get("/mercurial").toString(), Paths.get("/mercurial/closed").toString())),
                repos);

        // Test the types. There should be only one type for project with
        // multiple nested Mercurial repositories.

        List<String> types = target("projects")
                .path("mercurial")
                .path("repositories/type")
                .request()
                .get(type);

        assertEquals(Collections.singletonList("Mercurial"), types);
    }

    @Test
    void testSetIndexed() throws Exception {
        String project = "git";
        addProject(project);
        assertEquals(1, env.getProjectList().size());

        env.getProjects().get(project).setIndexed(false);
        assertFalse(env.getProjects().get(project).isIndexed());
        Response response = target("projects")
                .path(project)
                .path("property/indexed")
                .request()
                .put(Entity.text(Boolean.TRUE.toString()));
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertTrue(env.getProjects().get(project).isIndexed());
    }

    @Test
    void testSetGet() throws Exception {
        assertTrue(env.isHandleHistoryOfRenamedFiles());
        String[] projects = new String[] {"mercurial", "git"};

        for (String proj : projects) {
            addProject(proj);
        }

        assertEquals(2, env.getProjectList().size());
        for (String proj : projects) {
            Project project = env.getProjects().get(proj);
            assertNotNull(project);
            assertTrue(project.isHandleRenamedFiles());
            List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
            assertNotNull(riList);
            for (RepositoryInfo ri : riList) {
                ri.setHandleRenamedFiles(true);
                assertTrue(ri.isHandleRenamedFiles());
            }
        }

        // Change their property via RESTful API call.
        for (String proj : projects) {
            setHandleRenamedFilesToFalse(proj);
        }

        // Verify the property was set on each project and its repositories.
        for (String proj : projects) {
            Project project = env.getProjects().get(proj);
            assertNotNull(project);
            assertFalse(project.isHandleRenamedFiles());
            List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
            assertNotNull(riList);
            for (RepositoryInfo ri : riList) {
                assertFalse(ri.isHandleRenamedFiles());
            }
        }

        // Verify the property can be retrieved via message.
        for (String proj : projects) {
            boolean value = target("projects")
                    .path(proj)
                    .path("property/handleRenamedFiles")
                    .request()
                    .get(boolean.class);
            assertFalse(value);
        }
    }

    private void setHandleRenamedFilesToFalse(final String project) {
        target("projects")
                .path(project)
                .path("property/handleRenamedFiles")
                .request()
                .put(Entity.text(Boolean.FALSE.toString()));
    }

    @Test
    void testListFiles() throws IOException, IndexerException {
        final String projectName = "mercurial";
        GenericType<List<String>> type = new GenericType<>() {
        };

        Indexer.getInstance().prepareIndexer(
                env,
                false, // don't search for repositories
                true, // add projects
                // don't create dictionary
                new ArrayList<>(), // subFiles - needed when refreshing history partially
                new ArrayList<>()); // repositories - needed when refreshing history partially
        Indexer.getInstance().doIndexerExecution(null, null);

        List<String> filesFromRequest = target("projects")
                .path(projectName)
                .path("files")
                .request()
                .get(type);
        filesFromRequest.sort(String::compareTo);
        String[] files = {"Makefile", "bar.txt", "header.h", "main.c", "novel.txt"};
        for (int i = 0; i < files.length; i++) {
            files[i] = "/" + projectName + "/" + files[i];
        }
        List<String> expectedFiles = Arrays.asList(files);
        expectedFiles.sort(String::compareTo);

        assertEquals(expectedFiles, filesFromRequest);
    }
}
