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
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2020, Ric Harris <harrisric@users.noreply.github.com>.
 */
package org.opengrok.indexer.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.MERCURIAL;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.SCCS;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.SUBVERSION;
import static org.opengrok.indexer.history.MercurialRepositoryTest.runHgCommand;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.DateUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.Filter;
import org.opengrok.indexer.configuration.IgnoredNames;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.TestRepository;

/**
 * Test file based history cache with special focus on incremental reindex.
 *
 * @author Vladimir Kotal
 */
class FileHistoryCacheTest {

    private static final String SVN_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
    private TestRepository repositories;
    private FileHistoryCache cache;

    private boolean savedFetchHistoryWhenNotInCache;
    private boolean savedIsHandleHistoryOfRenamedFiles;
    private boolean savedIsTagsEnabled;

    /**
     * Set up the test environment with repositories and a cache instance.
     */
    @BeforeEach
    public void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResource("/repositories"));

        // Needed for HistoryGuru to operate normally.
        env.setRepositories(repositories.getSourceRoot());

        cache = new FileHistoryCache();
        cache.initialize();

        savedFetchHistoryWhenNotInCache = env.isFetchHistoryWhenNotInCache();
        savedIsHandleHistoryOfRenamedFiles = env.isHandleHistoryOfRenamedFiles();
        savedIsTagsEnabled = env.isTagsEnabled();
    }

    /**
     * Clean up after the test. Remove the test repositories.
     */
    @AfterEach
    public void tearDown() {
        repositories.destroy();
        repositories = null;

        cache = null;

        env.setFetchHistoryWhenNotInCache(savedFetchHistoryWhenNotInCache);
        env.setIgnoredNames(new IgnoredNames());
        env.setIncludedNames(new Filter());
        env.setHandleHistoryOfRenamedFiles(savedIsHandleHistoryOfRenamedFiles);
        env.setTagsEnabled(savedIsTagsEnabled);
    }

    /**
     * Assert that two lists of HistoryEntry objects are equal.
     *
     * @param expected the expected list of entries
     * @param actual the actual list of entries
     * @param isdir was the history generated for a directory
     * @throws AssertionError if the two lists don't match
     */
    private void assertSameEntries(List<HistoryEntry> expected, List<HistoryEntry> actual, boolean isdir) {
        assertEquals(expected.size(), actual.size(), "Unexpected size");
        Iterator<HistoryEntry> actualIt = actual.iterator();
        for (HistoryEntry expectedEntry : expected) {
            assertSameEntry(expectedEntry, actualIt.next(), isdir);
        }
        assertFalse(actualIt.hasNext(), "More entries than expected");
    }

    /**
     * Assert that two HistoryEntry objects are equal.
     *
     * @param expected the expected instance
     * @param actual the actual instance
     * @param isdir was the history generated for directory
     * @throws AssertionError if the two instances don't match
     */
    private void assertSameEntry(HistoryEntry expected, HistoryEntry actual, boolean isdir) {
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getRevision(), actual.getRevision());
        assertEquals(expected.getDate(), actual.getDate());
        assertEquals(expected.getMessage(), actual.getMessage());
        if (isdir) {
            assertEquals(expected.getFiles().size(), actual.getFiles().size());
        } else {
            assertEquals(0, actual.getFiles().size());
        }
    }

    /**
     * {@link FileHistoryCache#get(File, Repository, boolean)} should not disturb history cache
     * if run between repository update and reindex.
     */
    @EnabledOnOs({OS.LINUX, OS.MAC, OS.SOLARIS, OS.AIX, OS.OTHER})
    @EnabledForRepository(MERCURIAL)
    @Test
    void testStoreTouchGet() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);

        cache.store(historyToStore, repo);

        // This makes sure that the file which contains the latest revision has indeed been created.
        assertEquals("9:8b340409b3a8", cache.getLatestCachedRevision(repo));

        File file = new File(reposRoot, "main.c");
        assertTrue(file.exists());
        FileTime fileTimeBeforeImport = Files.getLastModifiedTime(file.toPath());
        History historyBeforeImport = cache.get(file, repo, false);

        MercurialRepositoryTest.runHgCommand(reposRoot, "import",
                Paths.get(getClass().getResource("/history/hg-export.txt").toURI()).toString());
        FileTime fileTimeAfterImport = Files.getLastModifiedTime(file.toPath());
        assertTrue(fileTimeBeforeImport.compareTo(fileTimeAfterImport) < 0);

        // Simulates reindex, or at least its first part when history cache is updated.
        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        // This makes sure that the file which contains the latest revision has indeed been created.
        assertEquals("11:bbb3ce75e1b8", cache.getLatestCachedRevision(repo));

        /*
         * The history should not be disturbed.
         * Make sure that get() retrieved the history from cache. Mocking/spying static methods
         * (FileHistoryCache#readCache() in this case) is tricky so use the cache hits metric.
         */
        double cacheHitsBeforeGet = cache.getFileHistoryCacheHits();
        History historyAfterReindex = cache.get(file, repo, false);
        double cacheHitsAfterGet = cache.getFileHistoryCacheHits();
        assertNotNull(historyAfterReindex);
        assertEquals(1, cacheHitsAfterGet - cacheHitsBeforeGet);
    }

    /**
     * Basic tests for the {@code store()} method on cache with disabled
     * handling of renamed files.
     */
    @EnabledForRepository(MERCURIAL)
    @Test
    void testStoreAndGetNotRenamed() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);

        cache.store(historyToStore, repo);

        // This makes sure that the file which contains the latest revision
        // has indeed been created.
        assertEquals("9:8b340409b3a8", cache.getLatestCachedRevision(repo));

        // test reindex
        History historyNull = new History();
        cache.store(historyNull, repo);

        assertEquals("9:8b340409b3a8", cache.getLatestCachedRevision(repo));
    }

    /**
     * Test tagging by creating history cache for repository with one tag and
     * then importing couple of changesets which add both file changes and tags.
     * The last history entry before the import is important as it needs to be
     * retagged when old history is merged with the new one.
     */
    @EnabledForRepository(MERCURIAL)
    @Test
    void testStoreAndGetIncrementalTags() throws Exception {
        // Enable tagging of history entries.
        env.setTagsEnabled(true);

        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);

        // Store the history.
        cache.store(historyToStore, repo);

        // Avoid uncommitted changes.
        MercurialRepositoryTest.runHgCommand(reposRoot, "revert", "--all");

        // Add bunch of changesets with file based changes and tags.
        MercurialRepositoryTest.runHgCommand(reposRoot, "import",
                Paths.get(getClass().getResource("/history/hg-export-tag.txt").toURI()).toString());

        // Perform incremental reindex.
        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        // Verify tags in fileHistory for main.c which is the most interesting
        // file from the repository from the perspective of tags.
        File main = new File(reposRoot, "main.c");
        assertTrue(main.exists());
        History retrievedHistoryMainC = cache.get(main, repo, true);
        List<HistoryEntry> entries = retrievedHistoryMainC.getHistoryEntries();
        assertEquals(3, entries.size(), "Unexpected number of entries for main.c");
        HistoryEntry e0 = entries.get(0);
        assertEquals("13:3d386f6bd848", e0.getRevision(), "Unexpected revision for entry 0");
        assertEquals("tag3", retrievedHistoryMainC.getTags().get(e0.getRevision()),
                "Invalid tag list for revision 13");
        HistoryEntry e1 = entries.get(1);
        assertEquals("2:585a1b3f2efb", e1.getRevision(), "Unexpected revision for entry 1");
        assertEquals("tag2, tag1, start_of_novel", retrievedHistoryMainC.getTags().get(e1.getRevision()),
                "Invalid tag list for revision 2");
        HistoryEntry e2 = entries.get(2);
        assertEquals("1:f24a5fd7a85d", e2.getRevision(), "Unexpected revision for entry 2");
        assertNull(retrievedHistoryMainC.getTags().get(e2.getRevision()), "Invalid tag list for revision 1");

        // Reindex from scratch.
        String histCachePath = CacheUtil.getRepositoryCacheDataDirname(repo, cache);
        assertNotNull(histCachePath);
        File dir = new File(histCachePath);
        assertTrue(dir.isDirectory());
        cache.clear(repo);
        assertFalse(dir.exists());
        History freshHistory = repo.getHistory(reposRoot);
        cache.store(freshHistory, repo);
        assertTrue(dir.exists());

        History retrievedUpdatedHistoryMainC = cache.get(main, repo, true);
        assertSameEntries(retrievedHistoryMainC.getHistoryEntries(),
                retrievedUpdatedHistoryMainC.getHistoryEntries(), false);
        assertEquals(Map.of("13:3d386f6bd848", "tag3", "2:585a1b3f2efb", "tag2, tag1, start_of_novel"),
                retrievedUpdatedHistoryMainC.getTags());
    }

    /**
     * Basic tests for the {@code store()} and {@code get()} methods.
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC, OS.SOLARIS, OS.AIX, OS.OTHER})
    @EnabledForRepository(MERCURIAL)
    void testStoreAndGet() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");

        // The test expects support for renamed files.
        env.setHandleHistoryOfRenamedFiles(true);

        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);

        cache.store(historyToStore, repo);

        // test reindex
        History historyNull = new History();
        cache.store(historyNull, repo);

        // test get history for single file
        File makefile = new File(reposRoot, "Makefile");
        assertTrue(makefile.exists());

        History retrievedHistory = cache.get(makefile, repo, true);

        List<HistoryEntry> entries = retrievedHistory.getHistoryEntries();

        assertEquals(2, entries.size(), "Unexpected number of entries");

        final String TROND = "Trond Norbye <trond.norbye@sun.com>";

        Iterator<HistoryEntry> entryIt = entries.iterator();

        HistoryEntry e1 = entryIt.next();
        assertEquals(TROND, e1.getAuthor());
        assertEquals("2:585a1b3f2efb", e1.getRevision());
        assertEquals(0, e1.getFiles().size());

        HistoryEntry e2 = entryIt.next();
        assertEquals(TROND, e2.getAuthor());
        assertEquals("1:f24a5fd7a85d", e2.getRevision());
        assertEquals(0, e2.getFiles().size());

        assertFalse(entryIt.hasNext());

        // test get history for renamed file
        File novel = new File(reposRoot, "novel.txt");
        assertTrue(novel.exists());

        retrievedHistory = cache.get(novel, repo, true);

        entries = retrievedHistory.getHistoryEntries();

        assertEquals(6, entries.size(), "Unexpected number of entries");

        // test incremental update
        MercurialRepositoryTest.runHgCommand(reposRoot, "import",
                Paths.get(getClass().getResource("/history/hg-export.txt").toURI()).toString());

        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        File mainC = new File(reposRoot, "main.c");
        History updatedHistory = cache.get(mainC, repo, false);
        assertNotNull(updatedHistory);

        HistoryEntry newEntry1 = new HistoryEntry(
                "10:1e392ef0b0ed",
                new Date(1245446973L / 60 * 60 * 1000), // whole minutes only
                "xyz",
                "Return failure when executed with no arguments",
                true);
        HistoryEntry newEntry2 = new HistoryEntry(
                "11:bbb3ce75e1b8",
                new Date(1245447973L / 60 * 60 * 1000), // whole minutes only
                "xyz",
                "Do something else",
                true);

        LinkedList<HistoryEntry> updatedEntries = new LinkedList<>(
                updatedHistory.getHistoryEntries());
        assertSameEntry(newEntry2, updatedEntries.removeFirst(), false);
        assertSameEntry(newEntry1, updatedEntries.removeFirst(), false);

        // test clearing of cache
        String dirPath = CacheUtil.getRepositoryCacheDataDirname(repo, cache);
        assertNotNull(dirPath);
        File dir = new File(dirPath);
        assertTrue(dir.isDirectory());
        cache.clear(repo);
        assertFalse(dir.exists());

        cache.store(historyToStore, repo);
        // check that the data directory is non-empty
        assertTrue(dir.list().length > 0);
    }

    /**
     * Check how incremental reindex behaves when indexing changesets that
     * rename+change file.
     *
     * The scenario goes as follows:
     * - create Mercurial repository
     * - perform full reindex
     * - add changesets which renamed and modify a file
     * - perform incremental reindex
     * - change+rename the file again
     * - incremental reindex
     */
    @EnabledOnOs({OS.LINUX, OS.MAC, OS.SOLARIS, OS.AIX, OS.OTHER})
    @EnabledForRepository(MERCURIAL)
    @Test
    void testRenameFileThenDoIncrementalReindex() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        History updatedHistory;

        // The test expects support for renamed files.
        env.setHandleHistoryOfRenamedFiles(true);

        // Use tags for better coverage.
        env.setTagsEnabled(true);

        // Generate history index.
        // It is necessary to call getRepository() only after tags were enabled
        // to produce list of tags.
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);
        cache.store(historyToStore, repo);

        // Import changesets which rename one of the files in the repository.
        MercurialRepositoryTest.runHgCommand(reposRoot, "import",
            Paths.get(getClass().getResource("/history/hg-export-renamed.txt").toURI()).toString());

        // Perform incremental reindex.
        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        // Check changesets for the renames and changes of single file.
        File main2File = new File(reposRoot.toString() + File.separatorChar + "main2.c");
        updatedHistory = cache.get(main2File, repo, false);

        // Changesets e0-e3 were brought in by the import done above.
        HistoryEntry e0 = new HistoryEntry(
                "13:e55a793086da",
                new Date(1245447973L / 60 * 60 * 1000), // whole minutes only
                "xyz",
                "Do something else",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "12:97b5392fec0d",
                new Date(1393515253L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>",
                "rename2",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "11:5c203a0bc12b",
                new Date(1393515291L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>",
                "rename1",
                true);
        HistoryEntry e3 = new HistoryEntry(
                "10:1e392ef0b0ed",
                new Date(1245446973L / 60 * 60 * 1000), // whole minutes only
                "xyz",
                "Return failure when executed with no arguments",
                true);
        HistoryEntry e4 = new HistoryEntry(
                "2:585a1b3f2efb",
                new Date(1218571989L / 60 * 60 * 1000), // whole minutes only
                "Trond Norbye <trond.norbye@sun.com>",
                "Add lint make target and fix lint warnings",
                true);
        HistoryEntry e5 = new HistoryEntry(
                "1:f24a5fd7a85d",
                new Date(1218571413L / 60 * 60 * 1000), // whole minutes only
                "Trond Norbye <trond.norbye@sun.com>",
                "Created a small dummy program",
                true);

        History histConstruct = new History();
        LinkedList<HistoryEntry> entriesConstruct = new LinkedList<>();
        entriesConstruct.add(e0);
        entriesConstruct.add(e1);
        entriesConstruct.add(e2);
        entriesConstruct.add(e3);
        entriesConstruct.add(e4);
        entriesConstruct.add(e5);
        histConstruct.setHistoryEntries(entriesConstruct);
        assertEquals(6, updatedHistory.getHistoryEntries().size());
        assertSameEntries(histConstruct.getHistoryEntries(),
            updatedHistory.getHistoryEntries(), false);

        // Add some changes and rename the file again.
        MercurialRepositoryTest.runHgCommand(reposRoot, "import",
            Paths.get(getClass().getResource("/history/hg-export-renamed-again.txt").toURI()).toString());

        // Perform incremental reindex.
        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        HistoryEntry e6 = new HistoryEntry(
                "14:55c41cd4b348",
                new Date(1489505558L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>", "rename + cstyle",
                true);
        entriesConstruct = new LinkedList<>();
        entriesConstruct.add(e6);
        entriesConstruct.add(e0);
        entriesConstruct.add(e1);
        entriesConstruct.add(e2);
        entriesConstruct.add(e3);
        entriesConstruct.add(e4);
        entriesConstruct.add(e5);
        histConstruct.setHistoryEntries(entriesConstruct);

        // Check changesets for the renames and changes of single file.
        File main3File = new File(reposRoot.toString() + File.separatorChar + "main3.c");
        updatedHistory = cache.get(main3File, repo, false);
        assertEquals(7, updatedHistory.getHistoryEntries().size());
        assertSameEntries(histConstruct.getHistoryEntries(),
            updatedHistory.getHistoryEntries(), false);
    }

    /**
     * Make sure generating incremental history index in branched repository
     * with renamed file produces correct history for the renamed file
     * (i.e. there should not be history entries from the default branch made
     * there after the branch was created).
     */
    @EnabledOnOs({OS.LINUX, OS.MAC, OS.SOLARIS, OS.AIX, OS.OTHER})
    @EnabledForRepository(MERCURIAL)
    @Test
    void testRenamedFilePlusChangesBranched() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        History updatedHistory;

        // The test expects support for renamed files.
        env.setHandleHistoryOfRenamedFiles(true);

        // Use tags for better coverage.
        env.setTagsEnabled(true);

        // Branch the repo and add one changeset.
        runHgCommand(reposRoot, "unbundle",
            Paths.get(getClass().getResource("/history/hg-branch.bundle").toURI()).toString());

        // Import changesets which rename one of the files in the default branch.
        runHgCommand(reposRoot, "import",
            Paths.get(getClass().getResource("/history/hg-export-renamed.txt").toURI()).toString());

        // Switch to the newly created branch.
        runHgCommand(reposRoot, "update", "mybranch");

        // Generate history index.
        // It is necessary to call getRepository() only after tags were enabled
        // to produce list of tags.
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);
        cache.store(historyToStore, repo);

        // Import changesets which rename the file in the new branch.
        runHgCommand(reposRoot, "import",
            Paths.get(getClass().getResource("/history/hg-export-renamed-branched.txt").toURI()).toString());

        // Perform incremental reindex.
        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        // Check complete list of history entries for the renamed file.
        File testFile = new File(reposRoot.toString() + File.separatorChar + "blog.txt");
        updatedHistory = cache.get(testFile, repo, false);

        HistoryEntry e0 = new HistoryEntry(
                "15:709c7a27f9fa",
                new Date(1489160275L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>",
                "novels are so last century. Let's write a blog !",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "10:c4518ca0c841",
                new Date(1415483555L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>",
                "branched",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "8:6a8c423f5624",
                new Date(1362586899L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                "first words of the novel",
                true);
        HistoryEntry e3 = new HistoryEntry(
                "7:db1394c05268",
                new Date(1362586862L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                "book sounds too boring, let's do a novel !",
                true);
        HistoryEntry e4 = new HistoryEntry(
                "6:e386b51ddbcc",
                new Date(1362586839L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                "stub of chapter 1",
                true);
        HistoryEntry e5 = new HistoryEntry(
                "5:8706402863c6",
                new Date(1362586805L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                "I decided to actually start writing a book based on the first plaintext file.",
                true);
        HistoryEntry e6 = new HistoryEntry(
                "4:e494d67af12f",
                new Date(1362586747L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                "first change",
                true);
        HistoryEntry e7 = new HistoryEntry(
                "3:2058725c1470",
                new Date(1362586483L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                "initial checking of text files",
                true);

        History histConstruct = new History();
        LinkedList<HistoryEntry> entriesConstruct = new LinkedList<>();
        entriesConstruct.add(e0);
        entriesConstruct.add(e1);
        entriesConstruct.add(e2);
        entriesConstruct.add(e3);
        entriesConstruct.add(e4);
        entriesConstruct.add(e5);
        entriesConstruct.add(e6);
        entriesConstruct.add(e7);
        histConstruct.setHistoryEntries(entriesConstruct);
        assertSameEntries(histConstruct.getHistoryEntries(),
                updatedHistory.getHistoryEntries(), false);
    }

    /**
     * Make sure produces correct history where several files are renamed in a single commit.
     */
    @EnabledForRepository(SUBVERSION)
    @Test
    void testMultipleRenamedFiles() throws Exception {
        createSvnRepository();

        File reposRoot = new File(repositories.getSourceRoot(), "subversion");
        History updatedHistory;

        // The test expects support for renamed files.
        env.setHandleHistoryOfRenamedFiles(true);

        // Generate history index.
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);
        cache.store(historyToStore, repo);

        // Check complete list of history entries for the renamed file.
        File testFile = new File(reposRoot.toString() + File.separatorChar + "FileZ.txt");
        updatedHistory = cache.get(testFile, repo, false);
        assertEquals(3, updatedHistory.getHistoryEntries().size());

        HistoryEntry e0 = new HistoryEntry(
                "10",
                DateUtils.parseDate("2020-03-28T07:24:43.921Z", SVN_DATE_FORMAT),
                "RichardH",
                "Rename FileA to FileZ and FileB to FileX in a single commit",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "7",
                DateUtils.parseDate("2020-03-28T07:21:55.273Z", SVN_DATE_FORMAT),
                "RichardH",
                "Amend file A",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "6",
                DateUtils.parseDate("2020-03-28T07:21:05.888Z", SVN_DATE_FORMAT),
                "RichardH",
                "Add file A",
                true);

        History histConstruct = new History();
        LinkedList<HistoryEntry> entriesConstruct = new LinkedList<>();
        entriesConstruct.add(e0);
        entriesConstruct.add(e1);
        entriesConstruct.add(e2);
        histConstruct.setHistoryEntries(entriesConstruct);
        assertSameEntries(histConstruct.getHistoryEntries(), updatedHistory.getHistoryEntries(), false);
    }

    private void createSvnRepository() throws Exception {
        var svnLog = FileHistoryCacheTest.class.getResource("/history/svnlog.dump");
        Path tempDir = Files.createTempDirectory("opengrok");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                IOUtils.removeRecursive(tempDir);
            } catch (IOException e) {
                // ignore
            }
        }));
        String repo = tempDir.resolve("svn-repo").toString();
        var svnCreateRepoProcess = new ProcessBuilder("svnadmin", "create", repo).start();
        assertEquals(0, svnCreateRepoProcess.waitFor());

        var svnLoadRepoFromDumpProcess = new ProcessBuilder("svnadmin", "load", repo)
                .redirectInput(Paths.get(svnLog.toURI()).toFile())
                .start();
        assertEquals(0, svnLoadRepoFromDumpProcess.waitFor());

        var svnCheckoutProcess = new ProcessBuilder("svn", "checkout", Path.of(repo).toUri().toString(),
                Path.of(repositories.getSourceRoot()).resolve("subversion").toString())
                .start();
        assertEquals(0, svnCheckoutProcess.waitFor());
    }

    static void changeFileAndCommit(Git git, File file, String comment) throws Exception {
        String authorName = "Foo Bar";
        String authorEmail = "foo@bar.com";

        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            fos.write(comment.getBytes(StandardCharsets.UTF_8));
        }

        git.commit().setMessage(comment).setAuthor(authorName, authorEmail).setAll(true).call();
    }

    /**
     * Renamed files need special treatment when given repository supports per partes history retrieval.
     * Specifically, when a file is detected as renamed, its history needs to be retrieved with upper bound,
     * otherwise there would be duplicate history entries if there were subsequent changes to the file
     * in the following history chunks. This test prevents that.
     * @param maxCount maximum number of changesets to store in one go
     * @throws Exception on error
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void testRenamedFileHistoryWithPerPartes(int maxCount) throws Exception {
        File repositoryRoot = new File(repositories.getSourceRoot(), "gitRenamedPerPartes");
        assertTrue(repositoryRoot.mkdir());
        File fooFile = new File(repositoryRoot, "foo.txt");
        if (!fooFile.createNewFile()) {
            throw new IOException("Could not create file " + fooFile);
        }
        File barFile = new File(repositoryRoot, "bar.txt");

        // Looks like the file needs to have content of some length for the add/rm below
        // to be detected as file rename by the [J]Git heuristics.
        try (FileOutputStream fos = new FileOutputStream(fooFile)) {
            fos.write("foo bar foo bar foo bar".getBytes(StandardCharsets.UTF_8));
        }

        // Create a series of commits to one (renamed) file:
        //  - add the file
        //  - bunch of content commits to the file
        //  - rename the file
        //  - bunch of content commits to the renamed file
        try (Git git = Git.init().setDirectory(repositoryRoot).call()) {
            git.add().addFilepattern("foo.txt").call();
            changeFileAndCommit(git, fooFile, "initial");

            changeFileAndCommit(git, fooFile, "1");
            changeFileAndCommit(git, fooFile, "2");
            changeFileAndCommit(git, fooFile, "3");

            // git mv
            assertTrue(fooFile.renameTo(barFile));
            git.add().addFilepattern("bar.txt").call();
            git.rm().addFilepattern("foo.txt").call();
            git.commit().setMessage("rename").setAuthor("foo", "foo@bar").setAll(true).call();

            changeFileAndCommit(git, barFile, "4");
            changeFileAndCommit(git, barFile, "5");
            changeFileAndCommit(git, barFile, "6");
        }

        env.setHandleHistoryOfRenamedFiles(true);
        Repository repository = RepositoryFactory.getRepository(repositoryRoot);
        History history = repository.getHistory(repositoryRoot);
        assertEquals(1, history.getRenamedFiles().size());
        GitRepository gitRepository = (GitRepository) repository;
        GitRepository gitSpyRepository = Mockito.spy(gitRepository);
        Mockito.when(gitSpyRepository.getPerPartesCount()).thenReturn(maxCount);
        gitSpyRepository.createCache(cache, null);

        assertTrue(barFile.isFile());
        History updatedHistory = cache.get(barFile, gitSpyRepository, false);
        assertEquals(8, updatedHistory.getHistoryEntries().size());
    }

    /**
     * Make sure produces correct history for a renamed and moved file in Subversion.
     */
    @EnabledForRepository(SUBVERSION)
    @Test
    void testRenamedFile() throws Exception {
        createSvnRepository();

        File reposRoot = new File(repositories.getSourceRoot(), "subversion");
        History updatedHistory;

        // The test expects support for renamed files.
        env.setHandleHistoryOfRenamedFiles(true);

        // Generate history index.
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);
        cache.store(historyToStore, repo);

        // Check complete list of history entries for the renamed file.
        File testFile = new File(reposRoot.toString() + File.separatorChar
                            + "subfolder" + File.separatorChar + "TestFileRenamedAgain.txt");
        updatedHistory = cache.get(testFile, repo, false);
        assertEquals(4, updatedHistory.getHistoryEntries().size());

        HistoryEntry e0 = new HistoryEntry(
                "5",
                DateUtils.parseDate("2020-03-28T07:20:11.821Z", SVN_DATE_FORMAT),
                "RichardH",
                "Moved file to subfolder and renamed",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "3",
                DateUtils.parseDate("2020-03-28T07:19:03.145Z", SVN_DATE_FORMAT),
                "RichardH",
                "Edited content",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "2",
                DateUtils.parseDate("2020-03-28T07:18:29.481Z", SVN_DATE_FORMAT),
                "RichardH",
                "Rename file",
                true);
        HistoryEntry e3 = new HistoryEntry(
                "1",
                DateUtils.parseDate("2020-03-28T07:17:54.756Z", SVN_DATE_FORMAT),
                "RichardH",
                "Add initial file",
                true);

        History histConstruct = new History();
        LinkedList<HistoryEntry> entriesConstruct = new LinkedList<>();
        entriesConstruct.add(e0);
        entriesConstruct.add(e1);
        entriesConstruct.add(e2);
        entriesConstruct.add(e3);
        histConstruct.setHistoryEntries(entriesConstruct);
        assertSameEntries(histConstruct.getHistoryEntries(),
                updatedHistory.getHistoryEntries(), false);
    }

    private void checkNoHistoryFetchRepo(String repoName, String filename, boolean hasHistory) throws Exception {

        File reposRoot = new File(repositories.getSourceRoot(), repoName);

        // Make sure the file exists in the repository.
        File repoFile = new File(reposRoot, filename);
        assertTrue(repoFile.exists());

        // Try to fetch the history for given file. With default setting of
        // FetchHistoryWhenNotInCache this should get the history even if not in cache.
        History retrievedHistory = HistoryGuru.getInstance().getHistory(repoFile, true);
        assertEquals(hasHistory, retrievedHistory != null);
    }

    /*
     * Functional test for the FetchHistoryWhenNotInCache configuration option.
     */
    @EnabledForRepository({MERCURIAL, SCCS})
    @Test
    void testNoHistoryFetch() throws Exception {
        env.setFetchHistoryWhenNotInCache(false);

        // Pretend we are done with first phase of indexing.
        env.setIndexer(true);
        HistoryGuru.getInstance().setHistoryIndexDone();

        // First try repo with ability to fetch history for directories.
        checkNoHistoryFetchRepo("mercurial", "main.c", false);
        // Second try repo which can fetch history of individual files only.
        checkNoHistoryFetchRepo("teamware", "header.h", true);
    }

    /**
     * Test history when activating PathAccepter for ignoring files.
     */
    @EnabledOnOs({OS.LINUX, OS.MAC, OS.SOLARIS, OS.AIX, OS.OTHER})
    @EnabledForRepository(MERCURIAL)
    @Test
    void testStoreAndTryToGetIgnored() throws Exception {
        env.getIgnoredNames().add("f:Make*");

        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");

        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);

        cache.store(historyToStore, repo);

        // test reindex history
        History historyNull = new History();
        cache.store(historyNull, repo);

        // test get history for single file
        File makefile = new File(reposRoot, "Makefile");
        assertTrue(makefile.exists(), "" + makefile + " should exist");

        History retrievedHistory = cache.get(makefile, repo, true);
        assertNull(retrievedHistory, "history for Makefile should be null");

        // Gross that we can break encapsulation, but oh well.
        env.getIgnoredNames().clear();
        // Need to refresh the history since it was changed in the cache.store().
        historyToStore = repo.getHistory(reposRoot);
        cache.store(historyToStore, repo);
        retrievedHistory = cache.get(makefile, repo, true);
        assertNotNull(retrievedHistory, "history for Makefile should not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<java version=\"11.0.8\" class=\"java.beans.XMLDecoder\">\n" +
                    "  <object class=\"java.lang.Runtime\" method=\"getRuntime\">\n" +
                    "    <void method=\"exec\">\n" +
                    "      <array class=\"java.lang.String\" length=\"2\">\n" +
                    "        <void index=\"0\">\n" +
                    "          <string>/usr/bin/nc</string>\n" +
                    "        </void>\n" +
                    "        <void index=\"1\">\n" +
                    "          <string>-l</string>\n" +
                    "        </void>\n" +
                    "      </array>\n" +
                    "    </void>\n" +
                    "  </object>\n" +
                    "</java>",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<java version=\"11.0.8\" class=\"java.beans.XMLDecoder\">\n" +
                    "  <object class=\"java.lang.ProcessBuilder\">\n" +
                    "    <array class=\"java.lang.String\" length=\"1\" >\n" +
                    "      <void index=\"0\"> \n" +
                    "        <string>/usr/bin/curl https://oracle.com</string>\n" +
                    "      </void>\n" +
                    "    </array>\n" +
                    "    <void method=\"start\"/>\n" +
                    "  </object>\n" +
                    "</java>",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<java version=\"11.0.8\" class=\"java.beans.XMLDecoder\">\n" +
                    "  <object class = \"java.io.FileOutputStream\"> \n" +
                    "    <string>opengrok_test.txt</string>\n" +
                    "    <method name = \"write\">\n" +
                    "      <array class=\"byte\" length=\"3\">\n" +
                    "        <void index=\"0\"><byte>96</byte></void>\n" +
                    "        <void index=\"1\"><byte>96</byte></void>\n" +
                    "        <void index=\"2\"><byte>96</byte></void>\n" +
                    "      </array>\n" +
                    "    </method>\n" +
                    "    <method name=\"close\"/>\n" +
                    "  </object>\n" +
                    "</java>"
    })
    void testDeserializationOfNotWhiteListedClassThrowsError(final String exploit) {
        assertThrows(IllegalAccessError.class, () -> FileHistoryCache.readCache(exploit));
    }

    @Test
    void testReadCacheValid() throws IOException {
        File testFile = new File(FileHistoryCacheTest.class.getClassLoader().
                getResource("history/FileHistoryCache.java.gz").getFile());
        History history = FileHistoryCache.readCache(testFile);
        assertNotNull(history);
        assertEquals(30, history.getHistoryEntries().size());
    }
}
