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
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.File;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.After;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.MercurialRepositoryTest;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.util.IOUtils;

/**
 * Test indexer w.r.t. repositories.
 * @author Vladimir Kotal
 */
public class IndexerRepoTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    private TestRepository repository;

    @Before
    public void setUp() throws IOException {
        repository = new TestRepository();
        // For these tests we need Mercurial repository with renamed files.
        repository.create(HistoryGuru.class.getResourceAsStream("repositories.zip"));
    }

    @After
    public void tearDown() {
        repository.destroy();
    }

    /**
     * Test it is possible to disable history per project.
     */
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @ConditionalRun(RepositoryInstalled.GitInstalled.class)
    @Test
    public void testPerProjectHistoryGlobalOn() throws IndexerException, IOException, HistoryException {
        testPerProjectHistory(true);
    }

    /**
     * Test it is possible to enable history per project.
     */
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @ConditionalRun(RepositoryInstalled.GitInstalled.class)
    @Test
    public void testPerProjectHistoryGlobalOff() throws IndexerException, IOException, HistoryException {
        testPerProjectHistory(false);
    }

    private void testPerProjectHistory(boolean globalOn) throws IndexerException, IOException, HistoryException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // Make sure we start from scratch.
        Path dataRoot = Files.createTempDirectory("dataForPerProjectHistoryTest");
        env.setDataRoot(dataRoot.toString());
        env.setProjectsEnabled(true);
        env.setHistoryEnabled(globalOn);

        Project proj = new Project("mercurial", "/mercurial");
        proj.setHistoryEnabled(!globalOn);
        env.getProjects().clear();
        env.getProjects().put("mercurial", proj);

        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                false, // don't create dictionary
                null, // subFiles - not needed since we don't list files
                null); // repositories - not needed when not refreshing history

        File repoRoot = new File(env.getSourceRootFile(), "git");
        File fileInRepo = new File(repoRoot, "main.c");
        assertTrue(fileInRepo.exists());
        if (globalOn) {
            assertNotNull(HistoryGuru.getInstance().getHistory(fileInRepo));
        } else {
            assertNull(HistoryGuru.getInstance().getHistory(fileInRepo));
        }

        repoRoot = new File(env.getSourceRootFile(), "mercurial");
        fileInRepo = new File(repoRoot, "main.c");
        assertTrue(fileInRepo.exists());
        if (globalOn) {
            assertNull(HistoryGuru.getInstance().getHistory(fileInRepo));
        } else {
            assertNotNull(HistoryGuru.getInstance().getHistory(fileInRepo));
        }

        IOUtils.removeRecursive(dataRoot);
    }

    /**
     * Test that symlinked directories from source root get their relative
     * path set correctly in RepositoryInfo objects.
     */
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void testSymlinks() throws IndexerException, IOException {

        final String SYMLINK = "symlink";
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // Set source root to pristine directory so that there is only one
        // repository to deal with (which makes this faster and easier to write)
        // and clone the mercurial repository outside of the source root.
        Path realSource = Files.createTempDirectory("real");
        Path sourceRoot = Files.createTempDirectory("src");
        MercurialRepositoryTest.runHgCommand(sourceRoot.toFile(),
                "clone", repository.getSourceRoot() + File.separator + "mercurial",
                realSource.toString());

        // Create symlink from source root to the real repository.
        String symlinkPath = sourceRoot.toString() + File.separator + SYMLINK;
        Files.createSymbolicLink(Paths.get(symlinkPath), realSource);

        // Use alternative source root.
        env.setSourceRoot(sourceRoot.toString());
        // Need to have history cache enabled in order to perform scan of repositories.
        env.setHistoryEnabled(true);
        // Normally the Indexer would add the symlink automatically.
        env.setAllowedSymlinks(new HashSet<>(Arrays.asList(symlinkPath)));

        // Do a rescan of the projects, and only that (we don't care about
        // the other aspects of indexing in this test case).
        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                false, // don't create dictionary
                null, // subFiles - not needed since we don't list files
                null); // repositories - not needed when not refreshing history

        // Check the repository paths.
        List<RepositoryInfo> repos = env.getRepositories();
        assertEquals(repos.size(), 1);
        RepositoryInfo repo = repos.get(0);
        assertEquals(File.separator + SYMLINK, repo.getDirectoryNameRelative());
        String epath = sourceRoot.toString() + File.separator + SYMLINK;
        String apath = repo.getDirectoryName();
        assertTrue("Should match (with macOS leeway):\n" + epath + "\nv.\n" +
                apath, epath.equals(apath) || apath.equals("/private" + epath));

        // Check that history exists for a file in the repository.
        File repoRoot = new File(env.getSourceRootFile(), SYMLINK);
        File fileInRepo = new File(repoRoot, "main.c");
        assertTrue(fileInRepo.exists());
        assertTrue(HistoryGuru.getInstance().hasHistory(fileInRepo));
        assertTrue(HistoryGuru.getInstance().hasCacheForFile(fileInRepo));

        // cleanup
        IOUtils.removeRecursive(realSource);
        IOUtils.removeRecursive(sourceRoot);
    }
}
