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
 */
package org.opensolaris.opengrok.configuration.messages;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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
        env.removeAllMessages();
        
        // This should match Configuration constructor.
        env.setProjects(new ConcurrentHashMap<>());
        env.setRepositories(new ArrayList<RepositoryInfo>());
        env.getProjectRepositoriesMap().clear();

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
        // Add one project.
        Message m = new ProjectMessage();
        m.setText("add");
        m.addTag("mercurial");
        Assert.assertTrue(env.getRepositories().isEmpty());
        Assert.assertTrue(env.getProjects().isEmpty());

        // Add a sub-repository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        File subDir = new File(mercurialRoot, "usr");
        Assert.assertTrue(subDir.mkdir());
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
            "clone", mercurialRoot.getAbsolutePath(),
            mercurialRoot.getAbsolutePath() + File.separator + "usr" + File.separator + "closed");

        env.setScanningDepth(3);
        m.apply(env);
        Assert.assertTrue(env.getProjects().containsKey("mercurial"));
        Assert.assertEquals(1, env.getProjects().size());
        Assert.assertEquals(2, env.getRepositories().size());

        // Add more projects and check that they have been added incrementally.
        // At the same time, it checks that multiple projects can be added
        // with single message.
        m.setTags(new TreeSet<String>());
        m.addTag("git");
        m.addTag("svn");
        m.apply(env);
        Assert.assertEquals(3, env.getProjects().size());
        Assert.assertEquals(4, env.getRepositories().size());
        Assert.assertTrue(env.getProjects().containsKey("git"));
        Assert.assertTrue(env.getProjects().containsKey("svn"));
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

    @Test
    public void testDelete() throws Exception {
        env.setCtags(System.getProperty(ctagsProperty, "ctags"));

        assertTrue("No point in running indexer tests without valid ctags",
                RuntimeEnvironment.getInstance().validateExuberantCtags());

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
        m.addTag("git");
        m.addTag("svn");
        m.apply(env);
        Assert.assertEquals(1, env.getProjects().size());
        Assert.assertEquals(1, env.getRepositories().size());
        Assert.assertEquals(1, env.getProjectRepositoriesMap().size());

        // Test data removal.
        File dataRoot = env.getDataRootFile();
        for (String projectName : new String[]{"git", "svn"}) {
            for (String dirName : new String[]{"historycache",
                IndexDatabase.XREF_DIR, IndexDatabase.INDEX_DIR}) {
                    File dir = new File(env.getDataRootFile(),
                        dirName + File.separator + projectName);
                    Assert.assertFalse(dir.exists());
            }
        }
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
}
