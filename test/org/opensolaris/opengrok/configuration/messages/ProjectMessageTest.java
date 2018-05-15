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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.opensolaris.opengrok.history.GitRepository;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.MercurialRepository;
import org.opensolaris.opengrok.history.MercurialRepositoryTest;
import org.opensolaris.opengrok.history.RepositoryFactory;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.history.SubversionRepository;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.index.Indexer;

import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.processMessage;
import static org.opensolaris.opengrok.util.IOUtils.removeRecursive;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * test ProjectMessage handling
 *
 * @author Vladimir Kotal
 */
@ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
public class ProjectMessageTest {
    
    private RuntimeEnvironment env;

    private TestRepository repository;

    private MessageListener listener;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(new MercurialRepository().isWorking());
        Assume.assumeTrue(new SubversionRepository().isWorking());
        Assume.assumeTrue(new GitRepository().isWorking());

        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream(
                "repositories.zip"));

        env = RuntimeEnvironment.getInstance();
        listener = MessageTestUtils.initMessageListener(env);
        listener.removeAllMessages();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);
    }

    @After
    public void tearDown() {
        if (env != null) {
            listener.removeAllMessages();

            // This should match Configuration constructor.
            env.setProjects(new ConcurrentHashMap<>());
            env.setRepositories(new ArrayList<>());
            env.getProjectRepositoriesMap().clear();
        }

        listener.stopConfigurationListenerThread();
        repository.destroy();
    }

    @Test
    public void testValidate() {
        Message.Builder<ProjectMessage> builder = new Message.Builder<>(ProjectMessage.class);
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.addTag("foo");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.setText("text");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.setText(null);
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("mercurial");
        builder.setText("add");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setText("indexed");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        // The deletion will validate even though the project is not present
        // in the project map in the configuration. This is because such extended
        // validation is performed only when the message is being applied.
        builder.setText("delete");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        try {
            processMessage(listener, builder.build());
            Assert.assertTrue(true);
        } catch (Exception ex) {
            System.err.println("got exception: " + ex);
        }
        // Now add the project to the map and re-apply the message. This time
        // it should not end up with exception.
        String projectName = "mercurial";
        env.getProjects().put(projectName, new Project(projectName, "/" + projectName));
        try {
            processMessage(listener, builder.build());
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
        Message.Builder<ProjectMessage> builder = new Message.Builder<>(ProjectMessage.class)
                .setText("add")
                .addTag("mercurial");

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
        processMessage(listener, builder.build());

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
        builder.clearTags();
        builder.addTag("git");
        builder.addTag("svn");
        processMessage(listener, builder.build());
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
     * @throws Exception exception
     */
    @Test
    public void testRepositoryRefresh() throws Exception {
        Message m = new Message.Builder<>(ProjectMessage.class)
                .setText("add")
                .addTag("mercurial")
                .build();
        processMessage(listener, m);

        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
            "clone", mercurialRoot.getAbsolutePath(),
            mercurialRoot.getAbsolutePath() + File.separator + "closed");

        processMessage(listener, m);
        Assert.assertEquals(2, env.getRepositories().size());
        Assert.assertEquals(2, env.getProjectRepositoriesMap().get(Project.getProject(mercurialRoot)).size());

        // Delete the newly added repository to verify it will be removed from
        // configuration after the message is reapplied. This is necessary anyway
        // for proper per-test cleanup.
        removeRecursive(new File(mercurialRoot.getAbsolutePath() + File.separator + "closed").toPath());

        processMessage(listener, m);
        Assert.assertEquals(1, env.getRepositories().size());
        Assert.assertEquals(1, env.getProjectRepositoriesMap().get(Project.getProject(mercurialRoot)).size());
    }

    /**
     * This test needs to perform indexing so that it can be verified that
     * the delete message handling performs removal of the index data.
     * @throws Exception exception
     */
    @Test
    @ConditionalRun(CtagsInstalled.class)
    public void testDelete() throws Exception {
        String projectsToDelete[] = { "git", "svn" };

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
        Message.Builder builder = new Message.Builder<>(ProjectMessage.class)
                .setText("add")
                .addTag("mercurial")
                .addTag("git")
                .addTag("svn");

        Assert.assertEquals(0, env.getProjects().size());
        Assert.assertEquals(0, env.getRepositories().size());
        Assert.assertEquals(0, env.getProjectRepositoriesMap().size());
        processMessage(listener, builder.build());
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

        // Then remove multiple projects.
        builder.setText("delete");
        builder.clearTags();
        for (String p : projectsToDelete) {
            builder.addTag(p);
        }
        processMessage(listener, builder.build());
        Assert.assertEquals(1, env.getProjects().size());
        Assert.assertEquals(1, env.getRepositories().size());
        Assert.assertEquals(1, env.getProjectRepositoriesMap().size());

        // Test data removal.
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
        Message.Builder<ProjectMessage> builder = new Message.Builder<>(ProjectMessage.class)
                .setText("add")
                .addTag(projectName);
        processMessage(listener, builder.build());
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
        builder.setText("indexed");
        processMessage(listener, builder.build());
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
        Message.Builder<ProjectMessage> builder = new Message.Builder<>(ProjectMessage.class)
                .setText("add")
                .addTag("mercurial");
        processMessage(listener, builder.build());

        // Mark it as indexed.
        builder.setText("indexed");
        processMessage(listener, builder.build());

        // Add another project.
        builder.setText("add");
        builder.addTag("git");
        processMessage(listener, builder.build());

        builder.clearTags();
        builder.setText("list");
        String out = processMessage(listener, builder.build()).getData().get(0);
        Assert.assertTrue(out.contains("mercurial"));
        Assert.assertTrue(out.contains("git"));

        builder.setText("list-indexed");
        out = processMessage(listener, builder.build()).getData().get(0);
        Assert.assertTrue(out.contains("mercurial"));
        Assert.assertFalse(out.contains("git"));
    }

    @Test
    public void testGetRepos() throws Exception {
        // Try to get repos for non-existent project first.
        Message m = new Message.Builder<>(ProjectMessage.class)
                .setText("get-repos")
                .addTag("totally-nonexistent-project")
                .build();

        String out = processMessage(listener, m).getData().get(0);
        Assert.assertEquals("", out);

        // Create subrepository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
            "clone", mercurialRoot.getAbsolutePath(),
            mercurialRoot.getAbsolutePath() + File.separator + "closed");

        // Add a project
        m = new Message.Builder<>(ProjectMessage.class)
                .setText("add")
                .addTag("mercurial")
                .build();
        processMessage(listener, m);

        // Get repositories of the project.
        m = new Message.Builder<>(ProjectMessage.class)
                .setText("get-repos")
                .addTag("mercurial")
                .build();
        out = processMessage(listener, m).getData().get(0);

        // Perform cleanup of the subrepository in order not to interefere
        // with other tests.
        removeRecursive(new File(mercurialRoot.getAbsolutePath() +
                File.separator + "closed").toPath());

        // test
        Assert.assertEquals("/mercurial\n/mercurial/closed", out);

        // Test the types. There should be only one type for project with
        // multiple nested Mercurial repositories.
        m = new Message.Builder<>(ProjectMessage.class)
                .setText("get-repos-type")
                .addTag("mercurial")
                .build();
        out = processMessage(listener, m).getData().get(0);
        Assert.assertEquals("Mercurial", out);
    }
}
