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
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2020, Ric Harris <harrisric@users.noreply.github.com>.
 */
package org.opengrok.indexer.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opengrok.indexer.history.MercurialRepositoryTest.runHgCommand;

import java.io.File;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.condition.UnixPresent;
import org.opengrok.indexer.configuration.Filter;
import org.opengrok.indexer.configuration.IgnoredNames;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TandemPath;
import org.opengrok.indexer.util.TestRepository;

/**
 * Test file based history cache with special focus on incremental reindex.
 *
 * @author Vladimir Kotal
 */
public class FileHistoryCacheTest {

    private static final String SVN_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
    private TestRepository repositories;
    private FileHistoryCache cache;

    private boolean savedFetchHistoryWhenNotInCache;
    private int savedHistoryReaderTimeLimit;
    private boolean savedIsHandleHistoryOfRenamedFiles;
    private boolean savedIsTagsEnabled;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    /**
     * Set up the test environment with repositories and a cache instance.
     */
    @Before
    public void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResourceAsStream("repositories.zip"));

        cache = new FileHistoryCache();
        cache.initialize();

        savedFetchHistoryWhenNotInCache = env.isFetchHistoryWhenNotInCache();
        savedHistoryReaderTimeLimit = env.getHistoryReaderTimeLimit();
        savedIsHandleHistoryOfRenamedFiles = env.isHandleHistoryOfRenamedFiles();
        savedIsTagsEnabled = env.isTagsEnabled();
    }

    /**
     * Clean up after the test. Remove the test repositories.
     */
    @After
    public void tearDown() {
        repositories.destroy();
        repositories = null;

        cache = null;

        env.setFetchHistoryWhenNotInCache(savedFetchHistoryWhenNotInCache);
        env.setHistoryReaderTimeLimit(savedHistoryReaderTimeLimit);
        env.setIgnoredNames(new IgnoredNames());
        env.setIncludedNames(new Filter());
        env.setHandleHistoryOfRenamedFiles(savedIsHandleHistoryOfRenamedFiles);
        env.setTagsEnabled(savedIsTagsEnabled);
    }

    /**
     * Assert that two HistoryEntry objects are equal.
     *
     * @param expected the expected entry
     * @param actual the actual entry
     * @param isdir was the history generated for a directory
     * @throws AssertionError if the two entries don't match
     */
    private void assertSameEntries(
            List<HistoryEntry> expected, List<HistoryEntry> actual, boolean isdir) {
        assertEquals("Unexpected size", expected.size(), actual.size());
        Iterator<HistoryEntry> actualIt = actual.iterator();
        for (HistoryEntry expectedEntry : expected) {
            assertSameEntry(expectedEntry, actualIt.next(), isdir);
        }
        assertFalse("More entries than expected", actualIt.hasNext());
    }

    /**
     * Assert that two lists of HistoryEntry objects are equal.
     *
     * @param expected the expected list of entries
     * @param actual the actual list of entries
     * @param isdir was the history generated for directory
     * @throws AssertionError if the two lists don't match
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
        assertEquals(expected.getTags(), actual.getTags());
    }

    /**
     * Basic tests for the {@code store()} method on cache with disabled
     * handling of renamed files.
     */
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void testStoreAndGetNotRenamed() throws Exception {
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
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void testStoreAndGetIncrementalTags() throws Exception {
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

        // Check that the changesets were indeed applied and indexed.
        History updatedHistory = cache.get(reposRoot, repo, true);
        assertEquals("Unexpected number of history entries",
                15, updatedHistory.getHistoryEntries().size());

        // Verify tags in fileHistory for main.c which is the most interesting
        // file from the repository from the perspective of tags.
        File main = new File(reposRoot, "main.c");
        assertTrue(main.exists());
        History retrievedHistoryMainC = cache.get(main, repo, true);
        List<HistoryEntry> entries = retrievedHistoryMainC.getHistoryEntries();
        assertEquals("Unexpected number of entries for main.c",
                3, entries.size());
        HistoryEntry e0 = entries.get(0);
        assertEquals("Unexpected revision for entry 0", "13:3d386f6bd848",
                e0.getRevision());
        assertEquals("Invalid tag list for revision 13", "tag3", e0.getTags());
        HistoryEntry e1 = entries.get(1);
        assertEquals("Unexpected revision for entry 1", "2:585a1b3f2efb",
                e1.getRevision());
        assertEquals("Invalid tag list for revision 2",
                "tag2, tag1, start_of_novel", e1.getTags());
        HistoryEntry e2 = entries.get(2);
        assertEquals("Unexpected revision for entry 2", "1:f24a5fd7a85d",
                e2.getRevision());
        assertNull("Invalid tag list for revision 1", e2.getTags());

        // Reindex from scratch.
        File dir = new File(cache.getRepositoryHistDataDirname(repo));
        assertTrue(dir.isDirectory());
        cache.clear(repo);
        // We cannot call cache.get() here since it would read the history anew.
        // Instead check that the data directory does not exist anymore.
        assertFalse(dir.exists());
        History freshHistory = repo.getHistory(reposRoot);
        cache.store(freshHistory, repo);
        History updatedHistoryFromScratch = cache.get(reposRoot, repo, true);
        assertEquals("Unexpected number of history entries",
                freshHistory.getHistoryEntries().size(),
                updatedHistoryFromScratch.getHistoryEntries().size());

        // Verify that the result for the directory is the same as incremental
        // reindex.
        assertSameEntries(updatedHistory.getHistoryEntries(),
                updatedHistoryFromScratch.getHistoryEntries(), true);
        // Do the same for main.c.
        History retrievedUpdatedHistoryMainC = cache.get(main, repo, true);
        assertSameEntries(retrievedHistoryMainC.getHistoryEntries(),
                retrievedUpdatedHistoryMainC.getHistoryEntries(), false);
    }

    /**
     * Basic tests for the {@code store()} and {@code get()} methods.
     */
    @ConditionalRun(UnixPresent.class)
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void testStoreAndGet() throws Exception {
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

        assertEquals("Unexpected number of entries", 2, entries.size());

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

        assertEquals("Unexpected number of entries", 6, entries.size());

        // test get history for directory
        // Need to refresh history to store since the file lists were stripped
        // from it in the call to cache.store() above.
        historyToStore = repo.getHistory(reposRoot);
        History dirHistory = cache.get(reposRoot, repo, true);
        assertSameEntries(
                historyToStore.getHistoryEntries(),
                dirHistory.getHistoryEntries(), true);

        // test incremental update
        MercurialRepositoryTest.runHgCommand(reposRoot, "import",
                Paths.get(getClass().getResource("/history/hg-export.txt").toURI()).toString());

        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        History updatedHistory = cache.get(reposRoot, repo, true);

        HistoryEntry newEntry1 = new HistoryEntry(
                "10:1e392ef0b0ed",
                new Date(1245446973L / 60 * 60 * 1000), // whole minutes only
                "xyz",
                null,
                "Return failure when executed with no arguments",
                true);
        newEntry1.addFile("/mercurial/main.c");
        HistoryEntry newEntry2 = new HistoryEntry(
                "11:bbb3ce75e1b8",
                new Date(1245447973L / 60 * 60 * 1000), // whole minutes only
                "xyz",
                null,
                "Do something else",
                true);
        newEntry2.addFile("/mercurial/main.c");

        LinkedList<HistoryEntry> updatedEntries = new LinkedList<>(
                updatedHistory.getHistoryEntries());
        // The history for retrieved for the whole directory so it will contain
        // lists of files so we need to set isdir to true.
        assertSameEntry(newEntry2, updatedEntries.removeFirst(), true);
        assertSameEntry(newEntry1, updatedEntries.removeFirst(), true);
        assertSameEntries(historyToStore.getHistoryEntries(), updatedEntries, true);

        // test clearing of cache
        File dir = new File(cache.getRepositoryHistDataDirname(repo));
        assertTrue(dir.isDirectory());
        cache.clear(repo);
        // We cannot call cache.get() here since it would read the history anew.
        // Instead check that the data directory does not exist anymore.
        assertFalse(dir.exists());

        cache.store(historyToStore, repo);
        // check that the data directory is non-empty
        assertTrue(dir.list().length > 0);
        updatedHistory = cache.get(reposRoot, repo, true);
        assertSameEntries(updatedHistory.getHistoryEntries(),
                cache.get(reposRoot, repo, true).getHistoryEntries(), true);
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
    @ConditionalRun(UnixPresent.class)
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void testRenameFileThenDoIncrementalReindex() throws Exception {
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

        // Verify size of complete history for the directory.
        updatedHistory = cache.get(reposRoot, repo, true);
        assertEquals(14, updatedHistory.getHistoryEntries().size());

        // Check changesets for the renames and changes of single file.
        File main2File = new File(reposRoot.toString() + File.separatorChar + "main2.c");
        updatedHistory = cache.get(main2File, repo, false);

        // Changesets e0-e3 were brought in by the import done above.
        HistoryEntry e0 = new HistoryEntry(
                "13:e55a793086da",
                new Date(1245447973L / 60 * 60 * 1000), // whole minutes only
                "xyz",
                null,
                "Do something else",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "12:97b5392fec0d",
                new Date(1393515253L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>",
                null,
                "rename2",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "11:5c203a0bc12b",
                new Date(1393515291L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>",
                null,
                "rename1",
                true);
        HistoryEntry e3 = new HistoryEntry(
                "10:1e392ef0b0ed",
                new Date(1245446973L / 60 * 60 * 1000), // whole minutes only
                "xyz",
                null,
                "Return failure when executed with no arguments",
                true);
        HistoryEntry e4 = new HistoryEntry(
                "2:585a1b3f2efb",
                new Date(1218571989L / 60 * 60 * 1000), // whole minutes only
                "Trond Norbye <trond.norbye@sun.com>",
                "start_of_novel",
                "Add lint make target and fix lint warnings",
                true);
        HistoryEntry e5 = new HistoryEntry(
                "1:f24a5fd7a85d",
                new Date(1218571413L / 60 * 60 * 1000), // whole minutes only
                "Trond Norbye <trond.norbye@sun.com>",
                null,
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
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>", null, "rename + cstyle",
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
    @ConditionalRun(UnixPresent.class)
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void testRenamedFilePlusChangesBranched() throws Exception {
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

        /* quick sanity check */
        updatedHistory = cache.get(reposRoot, repo, true);
        assertEquals(11, updatedHistory.getHistoryEntries().size());

        // Import changesets which rename the file in the new branch.
        runHgCommand(reposRoot, "import",
            Paths.get(getClass().getResource("/history/hg-export-renamed-branched.txt").toURI()).toString());

        // Perform incremental reindex.
        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        /* overall history check */
        updatedHistory = cache.get(reposRoot, repo, false);
        assertEquals(12, updatedHistory.getHistoryEntries().size());

        // Check complete list of history entries for the renamed file.
        File testFile = new File(reposRoot.toString() + File.separatorChar + "blog.txt");
        updatedHistory = cache.get(testFile, repo, false);

        HistoryEntry e0 = new HistoryEntry(
                "15:709c7a27f9fa",
                new Date(1489160275L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>",
                null,
                "novels are so last century. Let's write a blog !",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "10:c4518ca0c841",
                new Date(1415483555L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>",
                null,
                "branched",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "8:6a8c423f5624",
                new Date(1362586899L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                null,
                "first words of the novel",
                true);
        HistoryEntry e3 = new HistoryEntry(
                "7:db1394c05268",
                new Date(1362586862L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                "start_of_novel",
                "book sounds too boring, let's do a novel !",
                true);
        HistoryEntry e4 = new HistoryEntry(
                "6:e386b51ddbcc",
                new Date(1362586839L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                null,
                "stub of chapter 1",
                true);
        HistoryEntry e5 = new HistoryEntry(
                "5:8706402863c6",
                new Date(1362586805L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                null,
                "I decided to actually start writing a book based on the first plaintext file.",
                true);
        HistoryEntry e6 = new HistoryEntry(
                "4:e494d67af12f",
                new Date(1362586747L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                null,
                "first change",
                true);
        HistoryEntry e7 = new HistoryEntry(
                "3:2058725c1470",
                new Date(1362586483L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <vlada@devnull.cz>",
                null,
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
    @ConditionalRun(RepositoryInstalled.SubversionInstalled.class)
    @Test
    public void testMultipleRenamedFiles() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "subversion");
        History updatedHistory;

        // The test expects support for renamed files.
        env.setHandleHistoryOfRenamedFiles(true);

        // Generate history index.
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);
        cache.store(historyToStore, repo);

        /* quick sanity check */
        updatedHistory = cache.get(reposRoot, repo, true);
        assertEquals(10, updatedHistory.getHistoryEntries().size());

        // Check complete list of history entries for the renamed file.
        File testFile = new File(reposRoot.toString() + File.separatorChar + "FileZ.txt");
        updatedHistory = cache.get(testFile, repo, false);
        assertEquals(3, updatedHistory.getHistoryEntries().size());

        HistoryEntry e0 = new HistoryEntry(
                "10",
                DateUtils.parseDate("2020-03-28T07:24:43.921Z", SVN_DATE_FORMAT),
                "RichardH",
                null,
                "Rename FileA to FileZ and FileB to FileX in a single commit",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "7",
                DateUtils.parseDate("2020-03-28T07:21:55.273Z", SVN_DATE_FORMAT),
                "RichardH",
                null,
                "Amend file A",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "6",
                DateUtils.parseDate("2020-03-28T07:21:05.888Z", SVN_DATE_FORMAT),
                "RichardH",
                null,
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

    /**
     * Make sure produces correct history for a renamed and moved file in Subversion.
     */
    @ConditionalRun(RepositoryInstalled.SubversionInstalled.class)
    @Test
    public void testRenamedFile() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "subversion");
        History updatedHistory;

        // The test expects support for renamed files.
        env.setHandleHistoryOfRenamedFiles(true);

        // Generate history index.
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);
        cache.store(historyToStore, repo);

        /* quick sanity check */
        updatedHistory = cache.get(reposRoot, repo, true);
        assertEquals(10, updatedHistory.getHistoryEntries().size());

        // Check complete list of history entries for the renamed file.
        File testFile = new File(reposRoot.toString() + File.separatorChar
                            + "subfolder" + File.separatorChar + "TestFileRenamedAgain.txt");
        updatedHistory = cache.get(testFile, repo, false);
        assertEquals(4, updatedHistory.getHistoryEntries().size());

        HistoryEntry e0 = new HistoryEntry(
                "5",
                DateUtils.parseDate("2020-03-28T07:20:11.821Z", SVN_DATE_FORMAT),
                "RichardH",
                null,
                "Moved file to subfolder and renamed",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "3",
                DateUtils.parseDate("2020-03-28T07:19:03.145Z", SVN_DATE_FORMAT),
                "RichardH",
                null,
                "Edited content",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "2",
                DateUtils.parseDate("2020-03-28T07:18:29.481Z", SVN_DATE_FORMAT),
                "RichardH",
                null,
                "Rename file",
                true);
        HistoryEntry e3 = new HistoryEntry(
                "1",
                DateUtils.parseDate("2020-03-28T07:17:54.756Z", SVN_DATE_FORMAT),
                "RichardH",
                null,
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


    private void checkNoHistoryFetchRepo(String reponame, String filename,
            boolean hasHistory, boolean historyFileExists) throws Exception {

        File reposRoot = new File(repositories.getSourceRoot(), reponame);
        Repository repo = RepositoryFactory.getRepository(reposRoot);

        // Make sure the file exists in the repository.
        File repoFile = new File(reposRoot, filename);
        assertTrue(repoFile.exists());

        // Try to fetch the history for given file. With default setting of
        // FetchHistoryWhenNotInCache this should create corresponding file
        // in history cache.
        History retrievedHistory = cache.get(repoFile, repo, true);
        assertEquals(hasHistory, retrievedHistory != null);

        // The file in history cache should not exist since
        // FetchHistoryWhenNotInCache is set to false.
        File dataRoot = new File(repositories.getDataRoot(),
                "historycache" + File.separatorChar + reponame);
        File fileHistory = new File(dataRoot, TandemPath.join(filename, ".gz"));
        assertEquals(historyFileExists, fileHistory.exists());
    }

    /*
     * Functional test for the FetchHistoryWhenNotInCache configuration option.
     */
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @ConditionalRun(RepositoryInstalled.SCCSInstalled.class)
    @Test
    public void testNoHistoryFetch() throws Exception {
        // Do not create history cache for files which do not have it cached.
        env.setFetchHistoryWhenNotInCache(false);

        // Make cache.get() predictable. Normally when the retrieval of
        // history of given file is faster than the limit, the history of this
        // file is not stored. For the sake of this test we want the history
        // to be always stored.
        env.setHistoryReaderTimeLimit(0);

        // Pretend we are done with first phase of indexing.
        cache.setHistoryIndexDone();

        // First try repo with ability to fetch history for directories.
        checkNoHistoryFetchRepo("mercurial", "main.c", false, false);
        // Second try repo which can fetch history of individual files only.
        checkNoHistoryFetchRepo("teamware", "header.h", true, true);
    }

    /**
     * Test history when activating PathAccepter for ignoring files.
     */
    @ConditionalRun(UnixPresent.class)
    @ConditionalRun(RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void testStoreAndTryToGetIgnored() throws Exception {
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
        assertTrue("" + makefile + " should exist", makefile.exists());

        History retrievedHistory = cache.get(makefile, repo, true);
        assertNull("history for Makefile should be null", retrievedHistory);

        // Gross that we can break encapsulation, but oh well.
        env.getIgnoredNames().clear();
        cache.store(historyToStore, repo);
        retrievedHistory = cache.get(makefile, repo, true);
        assertNotNull("history for Makefile should not be null", retrievedHistory);
    }
}
