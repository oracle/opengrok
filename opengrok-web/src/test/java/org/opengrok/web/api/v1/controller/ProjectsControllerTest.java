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
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.MercurialRepositoryTest;
import org.opengrok.indexer.history.Repository;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.index.IndexDatabase;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.index.IndexerException;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opengrok.indexer.history.RepositoryFactory.getRepository;
import static org.opengrok.indexer.util.IOUtils.removeRecursive;

@ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
@ConditionalRun(RepositoryInstalled.GitInstalled.class)
@ConditionalRun(RepositoryInstalled.SubversionInstalled.class)
public class ProjectsControllerTest extends OGKJerseyTest {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Mock
    private SuggesterService suggesterService;

    @Override
    protected Application configure() {
        MockitoAnnotations.initMocks(this);
        return new ResourceConfig(ProjectsController.class)
                .register(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(suggesterService).to(SuggesterService.class);
                    }
                });
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
        env.setHandleHistoryOfRenamedFiles(true);
        RepositoryFactory.initializeIgnoredNames(env);
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
    public void testAddInherit() {
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

    private void addProject(final String project) {
        target("projects")
                .request()
                .post(Entity.text(project));
    }

    /**
     * Verify that added project correctly inherits a property
     * from configuration. Ideally, this should test all properties of Project.
     */
    @Test
    public void testAdd() throws Exception {
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
                getRepositories().stream().map(ri -> ri.getDirectoryName()).
                collect(Collectors.toSet());
        assertTrue("though it should contain the top root,",
                directoryNames.contains(repoPath) || directoryNames.contains(
                        mercurialRoot.getCanonicalPath()));
        assertTrue("though it should contain the sub-root,",
                directoryNames.contains(subRepoPath) || directoryNames.contains(
                        mercurialSubRoot.getCanonicalPath()));

        // Add more projects and check that they have been added incrementally.
        // At the same time, it checks that multiple projects can be added
        // with single message.

        addProject("git");
        addProject("svn");

        assertEquals(3, env.getProjects().size());
        assertEquals(4, env.getRepositories().size());
        assertTrue(env.getProjects().containsKey("git"));
        assertTrue(env.getProjects().containsKey("svn"));

        assertFalse(HistoryGuru.getInstance().getRepositories().stream().
                map(ri -> ri.getDirectoryName()).collect(Collectors.toSet()).
                contains("git"));
        assertFalse(HistoryGuru.getInstance().getRepositories().stream().
                map(ri -> ri.getDirectoryName()).collect(Collectors.toSet()).
                contains("svn"));
    }

    /**
     * Test that if the add is applied on already existing project,
     * the repository list is refreshed.
     */
    @Test
    public void testRepositoryRefresh() throws Exception {
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
     * the delete handling performs removal of the index data.
     */
    @Test
    public void testDelete() throws Exception {
        String[] projectsToDelete = {"git", "svn"};

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
        addProject("svn");

        assertEquals(3, env.getProjects().size());
        assertEquals(3, env.getRepositories().size());
        assertEquals(3, env.getProjectRepositoriesMap().size());

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
        subFiles.add("/svn");
        ArrayList<String> repos = new ArrayList<>();
        repos.add("/git");
        repos.add("/mercurial");
        repos.add("/svn");
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
                false, // don't create dictionary
                subFiles, // subFiles - needed when refreshing history partially
                repos); // repositories - needed when refreshing history partially
        Indexer.getInstance().doIndexerExecution(true, null, null);

        for (String proj : projectsToDelete) {
            delete(proj);
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
                    map(ri -> ri.getDirectoryName()).collect(Collectors.toSet()).
                    contains(repository.getSourceRoot() + File.separator + p));
        }

        // Check the group no longer contains the removed project.
        assertEquals(0, group.getRepositories().size());
        assertEquals(0, group.getProjects().size());
    }

    private void delete(final String project) {
        target("projects")
                .path(project)
                .request()
                .delete();
    }

    @Test
    public void testIndexed() throws IOException {
        String projectName = "mercurial";

        // When a project is added, it should be marked as not indexed.
        addProject(projectName);

        assertFalse(env.getProjects().get(projectName).isIndexed());

        // Get repository info for the project.
        Project project = env.getProjects().get(projectName);
        assertNotNull(project);
        List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
        assertNotNull(riList);
        assertEquals("there should be just 1 repository", 1, riList.size());
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

        temp.toFile().delete();

        // Test that the project's indexed flag becomes true only after
        // the message is applied.

        markIndexed(projectName);

        assertTrue("indexed flag should be set to true",
                env.getProjects().get(projectName).isIndexed());

        // Test that the "indexed" message triggers refresh of current version
        // info in related repositories.
        riList = env.getProjectRepositoriesMap().get(project);
        assertNotNull(riList);
        ri = riList.get(0);
        assertNotNull(ri);
        assertTrue("current version should be refreshed",
                ri.getCurrentVersion().contains("c78fa757c524"));
    }

    private void markIndexed(final String project) {
        target("projects")
                .path(project)
                .path("indexed")
                .request()
                .put(Entity.text(""));
    }

    @Test
    public void testList() {
        addProject("mercurial");
        markIndexed("mercurial");

        // Add another project.
        addProject("git");

        GenericType<List<String>> type = new GenericType<List<String>>() {
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
    public void testGetReposForNonExistentProject() throws Exception {
        GenericType<List<String>> type = new GenericType<List<String>>() {
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
    public void testGetRepos() throws Exception {
        GenericType<List<String>> type = new GenericType<List<String>>() {
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
    public void testSetGet() throws Exception {
        assertTrue(env.isHandleHistoryOfRenamedFiles());
        String[] projects = new String[] {"mercurial", "git"};

        for (String proj : projects) {
            addProject(proj);
        }

        assertEquals(2, env.getProjectList().size());

        // Change their property.
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
                Repository repo = getRepository(ri, CommandTimeoutType.RESTFUL);
                assertFalse(repo.isHandleRenamedFiles());
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
    public void testListFiles() throws IOException, IndexerException {
        final String projectName = "mercurial";
        GenericType<List<String>> type = new GenericType<List<String>>() {
        };

        Indexer.getInstance().prepareIndexer(
                env,
                false, // don't search for repositories
                true, // add projects
                false, // don't create dictionary
                new ArrayList<>(), // subFiles - needed when refreshing history partially
                new ArrayList<>()); // repositories - needed when refreshing history partially
        Indexer.getInstance().doIndexerExecution(true, null, null);

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
