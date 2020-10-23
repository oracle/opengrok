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
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.condition.UnixPresent;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.TreeSet;

/**
 * Represents a container for additional tests of {@link IndexDatabase} for symlinks.
 */
@ConditionalRun(UnixPresent.class)
@ConditionalRun(RepositoryInstalled.GitInstalled.class)
@ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
public class IndexDatabaseSymlinksTest {

    private static RuntimeEnvironment env;
    private static TestRepository repository;

    @ClassRule
    public static ConditionalRunRule rule = new ConditionalRunRule();

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();

        repository = new TestRepository();
        repository.createExternal(IndexDatabaseSymlinksTest.class.getResourceAsStream(
                "/index/links_tests.zip"));

        // Create and verify symlink from source/ to external/links_tests/links
        Path symlink = Paths.get(repository.getSourceRoot(), "links");
        Path target = Paths.get(repository.getExternalRoot(), "links_tests", "links");
        assertTrue(target + "should exist", target.toFile().exists());
        Files.createSymbolicLink(symlink, target);
        assertTrue(symlink + " should exist", symlink.toFile().exists());

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(true);
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);
    }

    @Before
    public void setUp() {
        repository.purgeData();
        env.setAllowedSymlinks(new HashSet<>());
        env.setCanonicalRoots(new HashSet<>());
    }

    @AfterClass
    public static void tearDownClass() {
        repository.destroy();
        env.setAllowedSymlinks(new HashSet<>());
        env.setCanonicalRoots(new HashSet<>());
    }

    @Test
    public void testNoAddedSymlinks() throws IOException, IndexerException {
        File canonicalSourceRoot = new File(repository.getSourceRoot()).getCanonicalFile();
        Path linksSourceDir = Paths.get(canonicalSourceRoot.getPath(), "links");

        /*
         * By "no added symlinks", we don't count default-accepted links
         * immediately under sourceRoot, which we do include here.
         */
        env.setAllowedSymlinks(new HashSet<>(Collections.singletonList(
                linksSourceDir.toString())));

        runIndexer();
        lsDir(env.getDataRootPath());

        Path xref = Paths.get(env.getDataRootPath(), "xref");
        assertFalse(xref + " should not exist", xref.toFile().exists());
    }

    @Test
    public void testSymlinksWithFullCanonicalRoot() throws IOException, IndexerException {
        File externalRoot = new File(repository.getExternalRoot());

        /*
         * For this test, don't even bother to include default-accepted links
         * immediately under sourceRoot, as --canonicalRoot as specified here
         * encompasses all of external/.
         */
        env.setCanonicalRoots(new HashSet<>(Collections.singletonList(
                externalRoot.getCanonicalPath())));
        runIndexer();
        lsDir(env.getDataRootPath());

        Path xref = Paths.get(env.getDataRootPath(), "xref");
        assertTrue(xref + " should exist", xref.toFile().exists());

        Path links = xref.resolve("links");
        assertTrue(links + " should exist", links.toFile().exists());

        Path gitDir = links.resolve("gt");
        assertTrue(gitDir + " should exist", gitDir.toFile().exists());
        Path subLink = gitDir.resolve("b");
        File expectedCanonical = gitDir.resolve("a").toFile().getCanonicalFile();
        assertSymlinkAsExpected("gt/b should == gt/a", expectedCanonical, subLink);

        Path mercurialDir = links.resolve("mrcrl");
        assertTrue(mercurialDir + " should exist", mercurialDir.toFile().exists());
        subLink = mercurialDir.resolve("b");
        expectedCanonical = mercurialDir.resolve("a").toFile().getCanonicalFile();
        assertSymlinkAsExpected("mrcrl/b should == mrcrl/a", expectedCanonical, subLink);

        Path dupeLinkDir = links.resolve("zzz");
        expectedCanonical = gitDir.toFile().getCanonicalFile();
        assertSymlinkAsExpected("zzz should == gt", expectedCanonical, dupeLinkDir);

        Path dupe2LinkDir = links.resolve("zzz_a");
        expectedCanonical = gitDir.resolve("a").toFile().getCanonicalFile();
        assertSymlinkAsExpected("zzz_a should == gt/a", expectedCanonical, dupe2LinkDir);
    }

    @Test
    public void testSymlinksWithOneAddedSymlink() throws IOException, IndexerException {
        File canonicalSourceRoot = new File(repository.getSourceRoot()).getCanonicalFile();
        Path linksSourceDir = Paths.get(canonicalSourceRoot.getPath(), "links");
        Path gitSourceDir = linksSourceDir.resolve("gt");
        assertTrue(gitSourceDir + " should exist", gitSourceDir.toFile().exists());

        /*
         * By "one added symlink", we don't count default-accepted links
         * immediately under sourceRoot, which we also include here.
         */
        env.setAllowedSymlinks(new HashSet<>(Arrays.asList(
                linksSourceDir.toString(), gitSourceDir.toString())));
        runIndexer();
        lsDir(env.getDataRootPath());

        Path xref = Paths.get(env.getDataRootPath(), "xref");
        assertTrue(xref + " should exist", xref.toFile().exists());

        Path links = xref.resolve("links");
        assertTrue(links + " should exist", links.toFile().exists());

        Path gitDir = links.resolve("gt");
        assertTrue(gitDir + " should exist", gitDir.toFile().exists());
        Path subLink = gitDir.resolve("b");
        File expectedCanonical = gitDir.resolve("a").toFile().getCanonicalFile();
        assertSymlinkAsExpected("gt/b should == gt/a", expectedCanonical, subLink);

        Path mercurialDir = links.resolve("mrcrl");
        assertFalse(mercurialDir + " should not exist", mercurialDir.toFile().exists());

        Path dupeLinkDir = links.resolve("zzz");
        /*
         * zzz is an implicitly-allowed symlink because its target matches an
         * already-indexed symlink, gt, and is reachable upon traversal by
         * indexDown() (to affirm that any intermediate symlinks are allowed).
         */
        expectedCanonical = gitDir.toFile().getCanonicalFile();
        assertSymlinkAsExpected("zzz should == gt", expectedCanonical, dupeLinkDir);

        /*
         * zzz_a is an implicitly-allowed symlink because its target matches as
         * a canonical child of an already-accepted symlink, gt.
         */
        Path dupe2LinkDir = links.resolve("zzz_a");
        expectedCanonical = gitDir.resolve("a").toFile().getCanonicalFile();
        assertSymlinkAsExpected("zzz_a should == gt/a", expectedCanonical, dupe2LinkDir);
    }

    private static void runIndexer() throws IndexerException, IOException {
        Indexer indexer = Indexer.getInstance();
        indexer.prepareIndexer(env, true, true, false, null, null);
        env.setDefaultProjectsFromNames(new TreeSet<>(Collections.singletonList("/c")));
        indexer.doIndexerExecution(true, null, null);
    }

    private void assertSymlinkAsExpected(String message, File expectedCanonical, Path symlink)
            throws IOException {
        assertTrue(symlink + " should exist", symlink.toFile().exists());
        assertTrue(symlink + " should be symlink", Files.isSymbolicLink(symlink));
        File actualCanonical = symlink.toFile().getCanonicalFile();
        assertTrue(actualCanonical + " should exist", actualCanonical.exists());
        assertEquals(message, expectedCanonical, actualCanonical);
    }

    private static void lsDir(String name) throws IOException {
        File file = Paths.get(name).toFile();
        if (!file.exists()) {
            return;
        }

        lsObj(file);
        if (Files.isSymbolicLink(file.toPath())) {
            return;
        }

        String[] fileList = file.list();
        if (fileList == null) {
            return;
        }

        for (String filename : fileList) {
            Path child = Paths.get(name, filename);
            lsDir(child.toString());
        }
    }

    private static void lsObj(File file) throws IOException {
        if (!file.exists()) {
            return;
        }

        Path file1 = file.toPath();

        System.out.print(file.getPath());
        if (Files.isSymbolicLink(file1)) {
            System.out.print(" -> ");
            System.out.print(Files.readSymbolicLink(file1));
        } else if (file.isDirectory()) {
            System.out.print("/");
        }
        System.out.println();
    }
}
