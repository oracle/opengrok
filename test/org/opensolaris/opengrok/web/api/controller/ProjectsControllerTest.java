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
package org.opensolaris.opengrok.web.api.controller;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opensolaris.opengrok.condition.ConditionalRun;
import org.opensolaris.opengrok.condition.ConditionalRunRule;
import org.opensolaris.opengrok.condition.CtagsInstalled;
import org.opensolaris.opengrok.condition.RepositoryInstalled;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.MercurialRepositoryTest;
import org.opensolaris.opengrok.history.Repository;
import org.opensolaris.opengrok.history.RepositoryFactory;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.index.Indexer;
import org.opensolaris.opengrok.util.TestRepository;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import java.io.File;
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
import static org.opensolaris.opengrok.history.RepositoryFactory.getRepository;
import static org.opensolaris.opengrok.util.IOUtils.removeRecursive;

@ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
@ConditionalRun(RepositoryInstalled.GitInstalled.class)
@ConditionalRun(RepositoryInstalled.SubvsersionInstalled.class)
public class ProjectsControllerTest extends JerseyTest {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repository;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Override
    protected Application configure() {
        return new ResourceConfig(ProjectsController.class);
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
        env.setHandleHistoryOfRenamedFiles(true);
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
    public void testAddInherit() {
        assertTrue(env.getRepositories().isEmpty());
        assertTrue(env.getProjects().isEmpty());
        assertTrue(env.isHandleHistoryOfRenamedFiles());

        addProjects("git");

        assertTrue(env.getProjects().containsKey("git"));
        assertEquals(1, env.getProjects().size());

        Project proj = env.getProjects().get("git");
        assertNotNull(proj);
        assertTrue(proj.isHandleRenamedFiles());
    }

    private void addProjects(String... projects) {
        target("projects")
                .request()
                .put(Entity.json(projects));
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

        addProjects("mercurial");

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

        addProjects("git", "svn");

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
        addProjects("mercurial");

        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
                "clone", mercurialRoot.getAbsolutePath(),
                mercurialRoot.getAbsolutePath() + File.separator + "closed");

        addProjects("mercurial");

        assertEquals(2, env.getRepositories().size());
        assertEquals(2, env.getProjectRepositoriesMap().get(Project.getProject(mercurialRoot)).size());

        // Delete the newly added repository to verify it will be removed from
        // configuration after the message is reapplied. This is necessary anyway
        // for proper per-test cleanup.
        removeRecursive(new File(mercurialRoot.getAbsolutePath() + File.separator + "closed").toPath());

        addProjects("mercurial");

        assertEquals(1, env.getRepositories().size());
        assertEquals(1, env.getProjectRepositoriesMap().get(Project.getProject(mercurialRoot)).size());
    }

    /**
     * This test needs to perform indexing so that it can be verified that
     * the delete handling performs removal of the index data.
     */
    @Test
    @ConditionalRun(CtagsInstalled.class)
    public void testDelete() throws Exception {
        String projectsToDelete[] = { "git", "svn" };

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

        addProjects("mercurial", "git", "svn");

        assertEquals(3, env.getProjects().size());
        assertEquals(3, env.getRepositories().size());
        assertEquals(3, env.getProjectRepositoriesMap().size());

        // Check the group was populated properly.
        assertEquals(1, group.getRepositories().size());
        assertEquals(0, group.getProjects().size());
        assertEquals(1, group.getRepositories().stream().
                filter(p -> p.getName().equals("git")).
                collect(Collectors.toSet()).size());

        // Run the indexer (ala 'indexpart') so that data directory is populated.
        ArrayList<String> subFiles = new ArrayList<>();
        subFiles.add("/git");
        subFiles.add("/mercurial");
        subFiles.add("/svn");
        ArrayList<String> repos = new ArrayList<>();
        repos.add("/git");
        repos.add("/mercurial");
        repos.add("/svn");
        // This is necessary so that repositories in HistoryGuru get populated.
        // When 'indexpart' is run, this is called from setConfiguration() because
        // of the -R option is present.
        HistoryGuru.getInstance().invalidateRepositories(
                env.getRepositories(), null, false);
        env.setHistoryEnabled(true);
        Indexer.getInstance().prepareIndexer(
                env,
                false, // don't search for repositories
                false, // don't scan and add projects
                null, // no default project
                false, // don't list files
                false, // don't create dictionary
                subFiles, // subFiles - needed when refreshing history partially
                repos, // repositories - needed when refreshing history partially
                new ArrayList<>(), // don't zap cache
                false); // don't list repos
        Indexer.getInstance().doIndexerExecution(true, null, null);

        target("projects")
                .queryParam("projects", (Object[]) projectsToDelete)
                .request()
                .delete();

        assertEquals(1, env.getProjects().size());
        assertEquals(1, env.getRepositories().size());
        assertEquals(1, env.getProjectRepositoriesMap().size());

        // Test data removal.
        for (String projectName : projectsToDelete) {
            for (String dirName : new String[]{"historycache",
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

    @Test
    public void testIndexed() {
        String projectName = "mercurial";

        // When a project is added, it should be marked as not indexed.
        addProjects(projectName);

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
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
                "import", HistoryGuru.getInstance().getClass().
                        getResource("hg-export-subdir.txt").getPath());

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

    private void markIndexed(String... projects) {
        target("projects")
                .path("markIndexed")
                .request()
                .post(Entity.json(projects));
    }

    @Test
    public void testList() {
        addProjects("mercurial");
        markIndexed("mercurial");

        // Add another project.
        addProjects("git");

        GenericType<List<String>> type = new GenericType<List<String>>() {};

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
    public void testGetRepos() throws Exception {
        GenericType<List<String>> type = new GenericType<List<String>>() {};

        // Try to get repos for non-existent project first.
        List<String> repos = target("projects")
                .path("repositories")
                .queryParam("projects", "totally-nonexistent-project")
                .request()
                .get(type);

        assertTrue(repos.isEmpty());

        // Create subrepository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
                "clone", mercurialRoot.getAbsolutePath(),
                mercurialRoot.getAbsolutePath() + File.separator + "closed");

        addProjects("mercurial");

        // Get repositories of the project.
        repos = target("projects")
                .path("repositories")
                .queryParam("projects", "mercurial")
                .request()
                .get(type);

        // Perform cleanup of the subrepository in order not to interefere
        // with other tests.
        removeRecursive(new File(mercurialRoot.getAbsolutePath() +
                File.separator + "closed").toPath());

        // test
        assertEquals(Arrays.asList("/mercurial", "/mercurial/closed"), repos);

        // Test the types. There should be only one type for project with
        // multiple nested Mercurial repositories.

        List<String> types = target("projects")
                .path("repositoriesType")
                .queryParam("projects", "mercurial")
                .request()
                .get(type);

        assertEquals(Collections.singletonList("Mercurial"), types);
    }

    @Test
    public void testSetGet() throws Exception {
        assertTrue(env.isHandleHistoryOfRenamedFiles());
        String[] projects = new String[] {"mercurial", "git"};

        addProjects(projects);

        assertEquals(2, env.getProjectList().size());

        // Change their property.

        target("projects")
                .path("property/handleRenamedFiles")
                .queryParam("projects", (Object[]) projects)
                .request()
                .put(Entity.text("false"));

        // Verify the property was set on each project and its repositories.
        for (String proj : projects) {
            Project project = env.getProjects().get(proj);
            assertNotNull(project);
            assertFalse(project.isHandleRenamedFiles());
            List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
            assertNotNull(riList);
            for (RepositoryInfo ri : riList) {
                Repository repo = getRepository(ri, false);
                assertFalse(repo.isHandleRenamedFiles());
            }
        }

        // Verify the property can be retrieved via message.
        for (String proj : projects) {
            boolean value = target("projects")
                    .path("property/handleRenamedFiles")
                    .queryParam("project", proj)
                    .request()
                    .get(boolean.class);
            assertFalse(value);
        }
    }

}
