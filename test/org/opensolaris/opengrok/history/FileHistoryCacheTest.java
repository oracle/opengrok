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
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.history;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import junit.framework.TestCase;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Test file based history cache with special focus on incremental reindex.
 * @author Vladimir Kotal
 */
public class FileHistoryCacheTest extends TestCase {
    private TestRepository repositories;
    private FileHistoryCache cache;
    
    /**
     * Set up the test environment with repositories and a cache instance.
     */
    @Override protected void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResourceAsStream("repositories.zip"));

        cache = new FileHistoryCache();
        cache.initialize();
    }

    /**
     * Clean up after the test. Remove the test repositories.
     */
    @Override protected void tearDown() throws Exception {
        repositories.destroy();
        repositories = null;

        cache = null;
    }
    
    /**
     * Import a new changeset into a Mercurial repository.
     *
     * @param reposRoot the root of the repository
     * @param changesetFile file that contains the changesets to import
     */
    private void importHgChangeset(File reposRoot, String changesetFile) {
        String[] cmdargs = {
            MercurialRepository.CMD_FALLBACK, "import", changesetFile
        };
        Executor exec = new Executor(Arrays.asList(cmdargs), reposRoot);
        int exitCode = exec.exec();
        if (exitCode != 0) {
            fail("hg import failed." +
                    "\nexit code: " + exitCode +
                    "\nstdout:\n" + exec.getOutputString() +
                    "\nstderr:\n" + exec.getErrorString());
        }
    }

    /**
     * Assert that two HistoryEntry objects are equal.
     * @param expected the expected entry
     * @param actual the actual entry
     * @param isdir was the history generated for a directory
     * @throws AssertFailure if the two entries don't match
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
     * @param expected the expected list of entries
     * @param actual the actual list of entries
     * @param isdir was the history generated for directory
     * @throws AssertFailure if the two lists don't match
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
     * Test tagging by creating history cache for repository with one tag
     * and then importing couple of changesets which add both file changes
     * and tags. The last history entry before the import is important
     * as it needs to be retagged when old history is merged with the new one.
     */
    public void testStoreAndGetIncrementalTags() throws Exception {
        // Enable tagging of history entries.
        RuntimeEnvironment.getInstance().setTagsEnabled(true);
        
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        History historyToStore = repo.getHistory(reposRoot);

        // Store the history.
        cache.store(historyToStore, repo);

        // Add bunch of changesets with file based changes and tags.
        importHgChangeset(
            reposRoot, getClass().getResource("hg-export-tag.txt").getPath());

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
        assertEquals("Invalid tag list for revision 1", null, e2.getTags());
        
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

        RuntimeEnvironment.getInstance().setTagsEnabled(false);
    }

    /**
     * Basic tests for the {@code store()} and {@code get()} methods.
     */
    public void testStoreAndGet() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");

        // The test expects support for renamed files.
        System.setProperty("org.opensolaris.opengrok.history.RenamedHandlingEnabled", "1");

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

        importHgChangeset(
                reposRoot, getClass().getResource("hg-export.txt").getPath());

        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        History updatedHistory = cache.get(reposRoot, repo, true);

        HistoryEntry newEntry1 = new HistoryEntry(
                "10:1e392ef0b0ed",
                new Date(1245446973L / 60 * 60 * 1000), // whole minutes only
                "xyz", null, "Return failure when executed with no arguments",
                true);
        newEntry1.addFile("/mercurial/main.c");
        HistoryEntry newEntry2 = new HistoryEntry(
                "11:bbb3ce75e1b8",
                new Date(1245447973L / 60 * 60 * 1000), // whole minutes only
                "xyz", null, "Do something else",
                true);
        newEntry2.addFile("/mercurial/main.c");
        
        LinkedList<HistoryEntry> updatedEntries = new LinkedList<HistoryEntry>(
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
        assertEquals(true, dir.list().length > 0);
        updatedHistory = cache.get(reposRoot, repo, true);
        assertSameEntries(updatedHistory.getHistoryEntries(),
                cache.get(reposRoot, repo, true).getHistoryEntries(), true);
    }

    /*
     * Test what happens when incremental reindex brings in changesets where
     * a file is renamed.
     */
    public void testRenamedFile() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository repo = RepositoryFactory.getRepository(reposRoot);

        // The test expects support for renamed files.
        System.setProperty("org.opensolaris.opengrok.history.RenamedHandlingEnabled", "1");

        History historyToStore = repo.getHistory(reposRoot);

        cache.store(historyToStore, repo);

        // import changesets which rename one of the files
        importHgChangeset(
            reposRoot, getClass().getResource("hg-export-renamed.txt").getPath());

        // reindex
        repo.createCache(cache, cache.getLatestCachedRevision(repo));

        // Check changesets for the renames and changes.
        File main2 = new File(reposRoot.toString() + File.separatorChar + "main2.c");
        History updatedHistory = cache.get(main2, repo, false);

        HistoryEntry e0 = new HistoryEntry(
                "13:e55a793086da",
                new Date(1245447973L / 60 * 60 * 1000), // whole minutes only
                "xyz", null, "Do something else",
                true);
        HistoryEntry e1 = new HistoryEntry(
                "12:97b5392fec0d",
                new Date(1393515253L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>", null, "rename2",
                true);
        HistoryEntry e2 = new HistoryEntry(
                "11:5c203a0bc12b",
                new Date(1393515291L / 60 * 60 * 1000), // whole minutes only
                "Vladimir Kotal <Vladimir.Kotal@oracle.com>", null, "rename1",
                true);
        HistoryEntry e3 = new HistoryEntry(
                "10:1e392ef0b0ed",
                new Date(1245446973L / 60 * 60 * 1000), // whole minutes only
                "xyz", null, "Return failure when executed with no arguments",
                true);
        HistoryEntry e4 = new HistoryEntry(
                "2:585a1b3f2efb",
                new Date(1218571989L / 60 * 60 * 1000), // whole minutes only
                "Trond Norbye <trond.norbye@sun.com>", null, "Add lint make target and fix lint warnings",
                true);
        HistoryEntry e5 = new HistoryEntry(
                "1:f24a5fd7a85d",
                new Date(1218571413L / 60 * 60 * 1000), // whole minutes only
                "Trond Norbye <trond.norbye@sun.com>", null, "Created a small dummy program",
                true);

        History histConstruct = new History();
        LinkedList<HistoryEntry> entriesConstruct = new LinkedList<HistoryEntry>();
        entriesConstruct.add(e0);
        entriesConstruct.add(e1);
        entriesConstruct.add(e2);
        entriesConstruct.add(e3);
        entriesConstruct.add(e4);
        entriesConstruct.add(e5);
        histConstruct.setHistoryEntries(entriesConstruct);
        assertSameEntries(histConstruct.getHistoryEntries(),
            updatedHistory.getHistoryEntries(), false);

        // Verify size of complete history for the directory.
        updatedHistory = cache.get(reposRoot, repo, true);
        assertEquals(14, updatedHistory.getHistoryEntries().size());
    }
}
