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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.GitRepository;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.MercurialRepository;
import org.opensolaris.opengrok.history.MercurialRepositoryTest;
import org.opensolaris.opengrok.history.RepositoryFactory;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.history.SubversionRepository;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.index.Indexer;
import static org.opensolaris.opengrok.util.IOUtils.removeRecursive;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * test ProjectMessage handling
 *
 * @author Vladimir Kotal
 */
public class ProjectMessageTest {
    
    RuntimeEnvironment env;

    private static TestRepository repository = new TestRepository();
    private final String ctagsProperty = "org.opensolaris.opengrok.analysis.Ctags";

    @Before
    public void setUp() throws IOException {
        Assume.assumeTrue(new MercurialRepository().isWorking());
        Assume.assumeTrue(new SubversionRepository().isWorking());
        Assume.assumeTrue(new GitRepository().isWorking());

        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream(
                "repositories.zip"));

        env = RuntimeEnvironment.getInstance();
        env.removeAllMessages();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);
    }

    @After
    public void tearDown() {
        if (env != null) {
            env.removeAllMessages();

            // This should match Configuration constructor.
            env.setProjects(new ConcurrentHashMap<>());
            env.setRepositories(new ArrayList<RepositoryInfo>());
            env.getProjectRepositoriesMap().clear();
        }

        repository.destroy();
    }

    @Test
    public void testValidate() {
        Message m = new ProjectMessage();
        Assert.assertFalse(MessageTest.assertValid(m));
        m.addTag("foo");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setText("text");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setText(null);
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setTags(new TreeSet<String>());
        m.addTag("mercurial");
        m.setText("add");
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setText("indexed");
        Assert.assertTrue(MessageTest.assertValid(m));
        // The deletion will validate even though the project is not present
        // in the project map in the configuration. This is because such extended
        // validation is performed only when the message is being applied.
        m.setText("delete");
        Assert.assertTrue(MessageTest.assertValid(m));
        try {
            m.apply(env);
            Assert.assertTrue(true);
        } catch (Exception ex) {
            System.err.println("got exception: " + ex);
        }
        // Now add the project to the map and re-apply the message. This time
        // it should not end up with exception.
        String projectName = "mercurial";
        env.getProjects().put(projectName, new Project(projectName, "/" + projectName));
        try {
            m.apply(env);
        } catch (Exception ex) {
            Assert.assertTrue(true);
            System.err.println("got exception: " + ex);
        }
    }

    @Test
    public void testAdd() throws Exception {
        Assert.assertTrue(env.getRepositories().isEmpty());
        Assert.assertTrue(env.getProjects().isEmpty());

        // Add a group matching the project to be added.
        String groupName = "mercurialgroup";
        Group group = new Group(groupName, "mercurial.*");
        env.getGroups().add(group);
        Assert.assertTrue(env.hasGroups());
        Assert.assertEquals(1, env.getGroups().stream().
                filter(g -> g.getName().equals(groupName)).
                collect(Collectors.toSet()).size());
        Assert.assertEquals(0, group.getRepositories().size());
        Assert.assertEquals(0, group.getProjects().size());

        // Prepare project addition.
        Message m = new ProjectMessage();
        m.setText("add");
        m.addTag("mercurial");

        // Add a sub-repository.
        String repoPath = repository.getSourceRoot() + File.separator + "mercurial";
        File mercurialRoot = new File(repoPath);
        File subDir = new File(mercurialRoot, "usr");
        Assert.assertTrue(subDir.mkdir());
        String subRepoPath = repoPath + File.separator + "usr" + File.separator + "closed";
        File mercurialSubRoot = new File(subRepoPath);
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
            "clone", mercurialRoot.getAbsolutePath(), subRepoPath);

        // Add the project.
        env.setScanningDepth(3);
        m.apply(env);

        // Check that the project was added properly.
        Assert.assertTrue(env.getProjects().containsKey("mercurial"));
        Assert.assertEquals(1, env.getProjects().size());
        Assert.assertEquals(2, env.getRepositories().size());
        Assert.assertEquals(1, group.getRepositories().size());
        Assert.assertEquals(0, group.getProjects().size());
        Assert.assertEquals(1, group.getRepositories().stream().
                filter(p -> p.getName().equals("mercurial")).
                collect(Collectors.toSet()).size());

        // Check that HistoryGuru now includes the project in its list.
        Set<String> directoryNames = HistoryGuru.getInstance().
            getRepositories().stream().map(ri -> ri.getDirectoryName()).
            collect(Collectors.toSet());
        Assert.assertTrue("though it should contain the top root,",
            directoryNames.contains(repoPath) || directoryNames.contains(
            mercurialRoot.getCanonicalPath()));
        Assert.assertTrue("though it should contain the sub-root,",
            directoryNames.contains(subRepoPath) || directoryNames.contains(
            mercurialSubRoot.getCanonicalPath()));
        
        // Add more projects and check that they have been added incrementally.
        // At the same time, it checks that multiple projects can be added
        // with single message.
        m.setTags(new TreeSet<>());
        m.addTag("git");
        m.addTag("svn");
        m.apply(env);
        Assert.assertEquals(3, env.getProjects().size());
        Assert.assertEquals(4, env.getRepositories().size());
        Assert.assertTrue(env.getProjects().containsKey("git"));
        Assert.assertTrue(env.getProjects().containsKey("svn"));
        
        Assert.assertFalse(HistoryGuru.getInstance().getRepositories().stream().
                map(ri -> ri.getDirectoryName()).collect(Collectors.toSet()).
                contains("git"));
        Assert.assertFalse(HistoryGuru.getInstance().getRepositories().stream().
                map(ri -> ri.getDirectoryName()).collect(Collectors.toSet()).
                contains("svn"));
    }

    /**
     * Test that if the "add" message is applied on already existing project,
     * the repository list is refreshed.
     */
    @Test
    public void testRepositoryRefresh() throws Exception {
        Message m = new ProjectMessage();
        m.setText("add");
        m.addTag("mercurial");
        m.apply(env);

        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
            "clone", mercurialRoot.getAbsolutePath(),
            mercurialRoot.getAbsolutePath() + File.separator + "closed");

        m.apply(env);
        Assert.assertEquals(2, env.getRepositories().size());
        Assert.assertEquals(2, env.getProjectRepositoriesMap().get(Project.getProject(mercurialRoot)).size());

        // Delete the newly added repository to verify it will be removed from
        // configuration after the message is reapplied. This is necessary anyway
        // for proper per-test cleanup.
        removeRecursive(new File(mercurialRoot.getAbsolutePath() + File.separator + "closed").toPath());

        m.apply(env);
        Assert.assertEquals(1, env.getRepositories().size());
        Assert.assertEquals(1, env.getProjectRepositoriesMap().get(Project.getProject(mercurialRoot)).size());
    }

    /**
     * This test needs to perform indexing so that it can be verified that
     * the delete message handling performs removal of the index data.
     * @throws Exception 
     */
    @Test
    public void testDelete() throws Exception {
        String projectsToDelete[] = { "git", "svn" };

        env.setCtags(System.getProperty(ctagsProperty, "ctags"));

        assertTrue("No point in running indexer tests without valid ctags",
                RuntimeEnvironment.getInstance().validateExuberantCtags());

        // Add a group matching the project to be added.
        String groupName = "gitgroup";
        Group group = new Group(groupName, "git.*");
        env.getGroups().add(group);
        Assert.assertTrue(env.hasGroups());
        Assert.assertEquals(1, env.getGroups().stream().
                filter(g -> g.getName().equals(groupName)).
                collect(Collectors.toSet()).size());
        Assert.assertEquals(0, group.getRepositories().size());
        Assert.assertEquals(0, group.getProjects().size());

        // Firstly add some projects.
        Message m = new ProjectMessage();
        m.setText("add");
        m.addTag("mercurial");
        m.addTag("git");
        m.addTag("svn");
        Assert.assertEquals(0, env.getProjects().size());
        Assert.assertEquals(0, env.getRepositories().size());
        Assert.assertEquals(0, env.getProjectRepositoriesMap().size());
        m.apply(env);
        Assert.assertEquals(3, env.getProjects().size());
        Assert.assertEquals(3, env.getRepositories().size());
        Assert.assertEquals(3, env.getProjectRepositoriesMap().size());

        // Check the group was populated properly.
        Assert.assertEquals(1, group.getRepositories().size());
        Assert.assertEquals(0, group.getProjects().size());
        Assert.assertEquals(1, group.getRepositories().stream().
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
            env.getRepositories());
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
        Indexer.getInstance().doIndexerExecution(true, 1, null, null);

        // Then remove multiple projects.
        m.setText("delete");
        m.setTags(new TreeSet<String>());
        for (String p : projectsToDelete) {
            m.addTag(p);
        }
        m.apply(env);
        Assert.assertEquals(1, env.getProjects().size());
        Assert.assertEquals(1, env.getRepositories().size());
        Assert.assertEquals(1, env.getProjectRepositoriesMap().size());

        // Test data removal.
        File dataRoot = env.getDataRootFile();
        for (String projectName : projectsToDelete) {
            for (String dirName : new String[]{"historycache",
                IndexDatabase.XREF_DIR, IndexDatabase.INDEX_DIR}) {
                    File dir = new File(env.getDataRootFile(),
                        dirName + File.separator + projectName);
                    Assert.assertFalse(dir.exists());
            }
        }

        // Check that HistoryGuru no longer maintains the removed projects.
        for (String p : projectsToDelete) {
            Assert.assertFalse(HistoryGuru.getInstance().getRepositories().stream().
                    map(ri -> ri.getDirectoryName()).collect(Collectors.toSet()).
                    contains(repository.getSourceRoot() + File.separator + p));
        }
        
        // Check the group no longer contains the removed project.
        Assert.assertEquals(0, group.getRepositories().size());
        Assert.assertEquals(0, group.getProjects().size());
    }

    @Test
    public void testIndexed() throws Exception {
        String projectName = "mercurial";

        // When a project is added, it should be marked as not indexed.
        Message m = new ProjectMessage();
        m.setText("add");
        m.addTag(projectName);
        m.apply(env);
        Assert.assertFalse(env.getProjects().get(projectName).isIndexed());

        // Get repository info for the project.
        Project project = env.getProjects().get(projectName);
        Assert.assertNotNull(project);
        List<RepositoryInfo> riList = env.getProjectRepositoriesMap().get(project);
        Assert.assertNotNull(riList);
        Assert.assertEquals("there should be just 1 repository", 1, riList.size());
        RepositoryInfo ri = riList.get(0);
        Assert.assertNotNull(ri);
        Assert.assertTrue(ri.getCurrentVersion().contains("8b340409b3a8"));

        // Add some changes to the repository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
            "import", HistoryGuru.getInstance().getClass().
            getResource("hg-export-subdir.txt").getPath());

        // Test that the project's indexed flag becomes true only after
        // the message is applied.
        m.setText("indexed");
        m.apply(env);
        Assert.assertTrue("indexed flag should be set to true",
                env.getProjects().get(projectName).isIndexed());

        // Test that the "indexed" message triggers refresh of current version
        // info in related repositories.
        riList = env.getProjectRepositoriesMap().get(project);
        Assert.assertNotNull(riList);
        ri = riList.get(0);
        Assert.assertNotNull(ri);
        Assert.assertTrue("current version should be refreshed",
                ri.getCurrentVersion().contains("c78fa757c524"));
    }

    @Test
    public void testList() throws Exception {
        // Add a project
        Message m = new ProjectMessage();
        m.setText("add");
        m.addTag("mercurial");
        m.apply(env);

        // Mark it as indexed.
        m.setText("indexed");
        m.apply(env);

        // Add another project.
        m.setText("add");
        m.addTag("git");
        m.apply(env);

        m.setTags(new TreeSet<String>());
        m.setText("list");
        String out = new String(m.apply(env));
        Assert.assertTrue(out.contains("mercurial"));
        Assert.assertTrue(out.contains("git"));

        m.setText("list-indexed");
        out = new String(m.apply(env));
        Assert.assertTrue(out.contains("mercurial"));
        Assert.assertFalse(out.contains("git"));
    }

    @Test
    public void testGetRepos() throws Exception {
        // Try to get repos for non-existent project first.
        Message m = new ProjectMessage();
        m.setText("get-repos");
        m.addTag("totally-nonexistent-project");
        String out = new String(m.apply(env));
        Assert.assertEquals("", out);

        // Create subrepository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
            "clone", mercurialRoot.getAbsolutePath(),
            mercurialRoot.getAbsolutePath() + File.separator + "closed");

        // Add a project
        m = new ProjectMessage();
        m.setText("add");
        m.addTag("mercurial");
        m.apply(env);

        // Get repositories of the project.
        m.setText("get-repos");
        out = new String(m.apply(env));

        // Perform cleanup of the subrepository in order not to interefere
        // with other tests.
        removeRecursive(new File(mercurialRoot.getAbsolutePath() +
                File.separator + "closed").toPath());

        // test
        Assert.assertEquals("/mercurial\n/mercurial/closed", out);

        // Test the types. There should be only one type for project with
        // multiple nested Mercurial repositories.
        m.setText("get-repos-type");
        out = new String(m.apply(env));
        Assert.assertEquals("Mercurial", out);
    }
}
