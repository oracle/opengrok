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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.CVS;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.MERCURIAL;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.SUBVERSION;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.FileUtilities;
import org.opengrok.indexer.util.TestRepository;

/**
 * Test the functionality provided by the HistoryGuru (with friends).
 * @author Trond Norbye
 * @author Vladimir Kotal
 */
public class HistoryGuruTest {

    private static TestRepository repository = new TestRepository();
    private static final List<File> FILES = new ArrayList<>();
    private static RuntimeEnvironment env;

    private static int savedNestingMaximum;

    @BeforeAll
    public static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();
        savedNestingMaximum = env.getNestingMaximum();

        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/repositories"));
        RepositoryFactory.initializeIgnoredNames(env);
        FileUtilities.getAllFiles(new File(repository.getSourceRoot()), FILES, true);
        assertNotEquals(0, FILES.size());

        HistoryGuru histGuru = HistoryGuru.getInstance();
        assertNotNull(histGuru);
        assertEquals(0, histGuru.getRepositories().size());

        // Add initial set of repositories to HistoryGuru and RuntimeEnvironment.
        // This is a test in itself. While this makes the structure of the tests
        // a bit incomprehensible, it does not make sense to run the rest of tests
        // if the basic functionality does not work.
        env.setRepositories(repository.getSourceRoot());
        assertTrue(histGuru.getRepositories().size() > 0);
        assertEquals(histGuru.getRepositories().size(),
                env.getRepositories().size());

        // Create cache with initial set of repositories.
        histGuru.createHistoryCache();
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
    }

    @AfterEach
    public void tearDown() {
        env.setNestingMaximum(savedNestingMaximum);
    }

    @Test
    void testGetRevision() throws HistoryException, IOException {
        HistoryGuru instance = HistoryGuru.getInstance();

        for (File f : FILES) {
            if (f.isFile() && instance.hasHistory(f)) {
                for (HistoryEntry entry : instance.getHistory(f).getHistoryEntries()) {
                    String revision = entry.getRevision();
                    try (InputStream in = instance.getRevision(f.getParent(), f.getName(), revision)) {
                        assertNotNull(in, "Failed to get revision " + revision
                                + " of " + f.getAbsolutePath());
                    }
                }
            }
        }
    }

    @Test
    @EnabledForRepository(SUBVERSION)
    void testBug16465() throws HistoryException, IOException {
        HistoryGuru instance = HistoryGuru.getInstance();
        for (File f : FILES) {
            if (f.getName().equals("bugreport16465@")) {
                assertNotNull(instance.getHistory(f), f.getPath() + " must have history");
                assertNotNull(instance.annotate(f, null), f.getPath() + " must have annotations");
            }
        }
    }

    @Test
    void annotation() throws Exception {
        HistoryGuru instance = HistoryGuru.getInstance();
        for (File f : FILES) {
            if (instance.hasAnnotation(f)) {
                assertNotNull(instance.annotate(f, null));
            }
        }
    }

    @Test
    void getCacheInfo() throws HistoryException {
        // FileHistoryCache is used by default
        assertEquals("FileHistoryCache", HistoryGuru.getInstance().getHistoryCacheInfo());
    }

    @Test
    void testAddRemoveRepositories() {
        HistoryGuru instance = HistoryGuru.getInstance();
        final int numReposOrig = instance.getRepositories().size();

        // Try to add non-existent repository.
        Collection<String> repos = new ArrayList<>();
        repos.add("totally-nonexistent-repository");
        Collection<RepositoryInfo> added = instance.addRepositories(repos);
        assertEquals(0, added.size());
        assertEquals(numReposOrig, instance.getRepositories().size());

        // Remove one repository.
        repos = new ArrayList<>();
        repos.add(env.getSourceRootPath() + File.separator + "git");
        instance.removeRepositories(repos);
        assertEquals(numReposOrig - 1, instance.getRepositories().size());

        // Add the repository back.
        added = instance.addRepositories(repos);
        assertEquals(1, added.size());
        assertEquals(numReposOrig, instance.getRepositories().size());
    }

    @Test
    @EnabledForRepository(CVS)
    void testAddSubRepositoryNotNestable() {
        HistoryGuru instance = HistoryGuru.getInstance();

        // Check out CVS underneath a Git repository.
        File cvsRoot = new File(repository.getSourceRoot(), "cvs_test");
        assertTrue(cvsRoot.exists());
        assertTrue(cvsRoot.isDirectory());
        File gitRoot = new File(repository.getSourceRoot(), "git");
        assertTrue(gitRoot.exists());
        assertTrue(gitRoot.isDirectory());
        CVSRepositoryTest.runCvsCommand(gitRoot, "-d",
                cvsRoot.toPath().resolve("cvsroot").toFile().getAbsolutePath(), "checkout", "cvsrepo");

        Collection<RepositoryInfo> addedRepos = instance.
                addRepositories(Collections.singleton(Paths.get(repository.getSourceRoot(),
                        "git").toString()));
        assertEquals(1, addedRepos.size());
    }

    @Test
    @EnabledForRepository(MERCURIAL)
    void testAddSubRepository() {
        HistoryGuru instance = HistoryGuru.getInstance();

        // Clone a Mercurial repository underneath a Mercurial repository.
        File hgRoot = new File(repository.getSourceRoot(), "mercurial");
        assertTrue(hgRoot.exists());
        assertTrue(hgRoot.isDirectory());
        MercurialRepositoryTest.runHgCommand(hgRoot,
                "clone", hgRoot.getAbsolutePath(), "subrepo");

        Collection<RepositoryInfo> addedRepos = instance.
                addRepositories(Collections.singleton(Paths.get(repository.getSourceRoot(),
                        "mercurial").toString()));
        assertEquals(2, addedRepos.size());
    }

    @Test
    void testNestingMaximum() throws IOException {
        // Just fake a nesting of Repo -> Git -> Git.
        File repoRoot = new File(repository.getSourceRoot(), "repoRoot");
        certainlyMkdirs(repoRoot);
        File repo0 = new File(repoRoot, ".repo");
        certainlyMkdirs(repo0);
        File sub1 = new File(repoRoot, "sub1");
        certainlyMkdirs(sub1);
        File repo1 = new File(sub1, ".git");
        certainlyMkdirs(repo1);
        File sub2 = new File(sub1, "sub2");
        certainlyMkdirs(sub2);
        File repo2 = new File(sub2, ".git");
        certainlyMkdirs(repo2);

        HistoryGuru instance = HistoryGuru.getInstance();
        Collection<RepositoryInfo> addedRepos = instance.addRepositories(
                Collections.singleton(Paths.get(repository.getSourceRoot(),
                        "repoRoot").toString()));
        assertEquals(2, addedRepos.size(), "should add to default nesting maximum");

        env.setNestingMaximum(2);
        addedRepos = instance.addRepositories(
                Collections.singleton(Paths.get(repository.getSourceRoot(),
                        "repoRoot").toString()));
        assertEquals(3, addedRepos.size(), "should get one more repo");
    }

    private static void certainlyMkdirs(File file) throws IOException {
        if (!file.mkdirs()) {
            throw new IOException("Couldn't mkdirs " + file);
        }
    }

    @Test
    void testScanningDepth() throws IOException {
        String topLevelDirName = "scanDepthTest";
        File repoRoot = new File(repository.getSourceRoot(), topLevelDirName);
        certainlyMkdirs(repoRoot);
        File repo0 = new File(repoRoot, ".git");
        certainlyMkdirs(repo0);
        File sub1 = new File(repoRoot, "sub1");
        certainlyMkdirs(sub1);
        File sub2 = new File(sub1, "sub2");
        certainlyMkdirs(sub2);
        File sub3 = new File(sub2, ".git");
        certainlyMkdirs(sub3);

        int originalScanDepth = env.getScanningDepth();
        env.setScanningDepth(0);

        HistoryGuru instance = HistoryGuru.getInstance();
        Collection<RepositoryInfo> addedRepos = instance.addRepositories(
                Collections.singleton(Paths.get(repository.getSourceRoot(), topLevelDirName).toString()));
        assertEquals(1, addedRepos.size(), "should add to max depth");

        env.setScanningDepth(1);
        List<String> repoDirs = addedRepos.stream().map(RepositoryInfo::getDirectoryName).collect(Collectors.toList());
        instance.removeRepositories(repoDirs);
        addedRepos = instance.addRepositories(
                Collections.singleton(Paths.get(repository.getSourceRoot(), topLevelDirName).toString()));
        assertEquals(2, addedRepos.size(), "should add to increased max depth");

        env.setScanningDepth(originalScanDepth);
    }

    @Test
    void testGetLastHistoryEntryNonexistent() throws HistoryException {
        HistoryGuru instance = HistoryGuru.getInstance();
        File file = new File("/nonexistent");
        assertThrows(HistoryException.class, () -> instance.getLastHistoryEntry(file, true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGetLastHistoryEntryVsIndexer(boolean isIndexerParam) throws HistoryException {
        boolean isIndexer = env.isIndexer();
        env.setIndexer(isIndexerParam);
        boolean isTagsEnabled = env.isTagsEnabled();
        env.setTagsEnabled(true);
        HistoryGuru instance = HistoryGuru.getInstance();
        File file = new File(repository.getSourceRoot(), "git");
        assertTrue(file.exists());
        if (isIndexerParam) {
            assertThrows(IllegalStateException.class, () -> instance.getLastHistoryEntry(file, true));
        } else {
            assertNotNull(instance.getLastHistoryEntry(file, true));
        }
        env.setIndexer(isIndexer);
        env.setTagsEnabled(isTagsEnabled);
    }

    @Test
    void testGetLastHistoryEntryRepoHistoryDisabled() throws Exception {
        File file = new File(repository.getSourceRoot(), "git");
        assertTrue(file.exists());
        HistoryGuru instance = HistoryGuru.getInstance();
        Repository repository = instance.getRepository(file);

        // HistoryGuru is final class so cannot be reasonably mocked with Mockito.
        // In order to avoid getting the history from the cache, move the cache away for a bit.
        String dirName = CacheUtil.getRepositoryCacheDataDirname(repository, new FileHistoryCache());
        assertNotNull(dirName);
        Path histPath = Path.of(dirName);
        Path tmpHistPath = Path.of(dirName + ".disabled");
        Files.move(histPath, tmpHistPath);
        assertFalse(histPath.toFile().exists());

        assertNotNull(repository);
        repository.setHistoryEnabled(false);
        assertNull(instance.getLastHistoryEntry(file, false));

        // cleanup
        repository.setHistoryEnabled(true);
        Files.move(tmpHistPath, histPath);
    }
}
