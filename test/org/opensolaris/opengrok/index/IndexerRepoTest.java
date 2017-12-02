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
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.index;

import java.io.File;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.condition.ConditionalRun;
import org.opensolaris.opengrok.condition.RepositoryInstalled;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.MercurialRepositoryTest;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.util.FileUtilities;
import org.opensolaris.opengrok.util.TestRepository;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Test indexer w.r.t. repositories.
 *
 * @author Vladimir Kotal
 */
public class IndexerRepoTest {

    TestRepository repository;
    private final String ctagsProperty = "org.opensolaris.opengrok.analysis.Ctags";

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

    private void checkNumberOfThreads() {
        /*
         * There should not be any threads in the renamed pool now.
         * We need to check it like this since the test framework tears
         * down the threads at the end of the test case run so any
         * hangs due to executors not being shut down would not be visible.
         */
        ThreadGroup mainGroup = Thread.currentThread().getThreadGroup();
        Thread[] threads = new Thread[mainGroup.activeCount()];
        mainGroup.enumerate(threads);
        for (int i = 0; i < threads.length; i++) {
            if (threads[i] == null || threads[i].getName() == null) {
                continue;
            }
            assertEquals(false, threads[i].getName().contains("renamed-handling"));
        }
    }

    /**
     * Test that symlinked directories from source root get their relative
     * path set correctly in RepositoryInfo objects.
     * @throws org.opensolaris.opengrok.index.IndexerException
     * @throws java.io.IOException
     */
    @ConditionalRun(condition = RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void testSymlinks() throws IndexerException, IOException {

        final String SYMLINK = "symlink";
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // Set source root to pristine directory so that there is only one
        // repository to deal with (which makes this faster and easier to write)
        // and clone the mercurial repository outside of the source root.
        File realSource = FileUtilities.createTemporaryDirectory("real");
        File sourceRoot = FileUtilities.createTemporaryDirectory("src");
        MercurialRepositoryTest.runHgCommand(sourceRoot,
                "clone", repository.getSourceRoot() + File.separator + "mercurial",
                realSource.getPath());

        // Create symlink from source root to the real repository.
        String symlinkPath = sourceRoot.toString() + File.separator + SYMLINK;
        Files.createSymbolicLink(Paths.get(symlinkPath), Paths.get(realSource.getPath()));

        env.setSourceRoot(sourceRoot.toString());
        env.setDataRoot(repository.getDataRoot());
        // Need to have history cache enabled in order to perform scan of repositories.
        env.setHistoryEnabled(true);
        // Normally the Indexer would add the symlink automatically.
        env.setAllowedSymlinks(new HashSet<String>(Arrays.asList(symlinkPath)));

        // Do a rescan of the projects, and only that (we don't care about
        // the other aspects of indexing in this test case).
        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                null, // no default project
                false, // don't list files
                false, // don't create dictionary
                null, // subFiles - not needed since we don't list files
                null, // repositories - not needed when not refreshing history
                new ArrayList<>(), // don't zap cache
                false); // don't list repos

        // Check the respository paths.
        List<RepositoryInfo> repos = env.getRepositories();
        assertEquals(repos.size(), 1);
        RepositoryInfo repo = repos.get(0);
        assertEquals("/" + SYMLINK, repo.getDirectoryNameRelative());
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
        IOUtils.removeRecursive(realSource.toPath());
        IOUtils.removeRecursive(sourceRoot.toPath());
    }

    /**
     * Test cleanup of renamed thread pool after indexing with -H.
     */
    @Test
    public void testMainWithH() throws IOException {
        System.out.println("Generate index by using command line options with -H");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty(ctagsProperty, "ctags"));
        if (env.validateExuberantCtags()) {
            String[] argv = {"-S", "-H", "-s", repository.getSourceRoot(),
                "-d", repository.getDataRoot(), "-v", "-c", env.getCtags()};
            Indexer.main(argv);
            checkNumberOfThreads();
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use.");
        }
    }

    /**
     * Test cleanup of renamed thread pool after indexing without -H.
     */
    @Test
    public void testMainWithoutH() throws IOException {
        System.out.println("Generate index by using command line options without -H");
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty(ctagsProperty, "ctags"));
        if (env.validateExuberantCtags()) {
            String[] argv = {"-S", "-P", "-s", repository.getSourceRoot(),
                "-d", repository.getDataRoot(), "-v", "-c", env.getCtags()};
            Indexer.main(argv);
            checkNumberOfThreads();
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use.");
        }
    }
}
