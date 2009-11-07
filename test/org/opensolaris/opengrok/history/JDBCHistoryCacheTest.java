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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Unit tests for {@code JDBCHistoryCache}.
 */
public class JDBCHistoryCacheTest extends TestCase {

    private TestRepository repositories;
    private JDBCHistoryCache cache;

    private static final String DERBY_EMBEDDED_DRIVER =
            "org.apache.derby.jdbc.EmbeddedDriver";

    public JDBCHistoryCacheTest(String name) {
        super(name);
    }

    /**
     * Create a suite of tests to run. If the Derby classes are not present,
     * skip this test.
     *
     * @return tests to run
     */
    public static Test suite() {
        try {
            Class.forName(DERBY_EMBEDDED_DRIVER);
            return new TestSuite(JDBCHistoryCacheTest.class);
        } catch (ClassNotFoundException e) {
            return new TestSuite("JDBCHistoryCacheTest - empty (no derby.jar)");
        }
    }

    /**
     * Set up the test environment with repositories and a cache instance.
     */
    @Override protected void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResourceAsStream("repositories.zip"));

        cache = new JDBCHistoryCache(
                DERBY_EMBEDDED_DRIVER, getURL() + ";create=true");
        cache.initialize();
    }

    /**
     * Clean up after the test. Remove the test repositories and shut down
     * the database.
     */
    @Override protected void tearDown() throws Exception {
        repositories.destroy();
        repositories = null;

        cache = null;

        try {
            DriverManager.getConnection(getURL() + ";shutdown=true");
        } catch (SQLException sqle) {
            // Expect SQLException with SQLState 08006 on successful shutdown
            if (!sqle.getSQLState().equals("08006")) {
                throw sqle;
            }
        }
    }

    /**
     * Create a database URL to use for this test. The URL points to an
     * in-memory Derby database.
     *
     * @return a database URL
     */
    private String getURL() {
        return "jdbc:derby:memory:DB-" + getName();
    }

    /**
     * Import a new changeset into a Mercurial repository.
     *
     * @param reposRoot the root of the repository
     * @param changesetFile file that contains the changeset to import
     */
    private void importHgChangeset(File reposRoot, String changesetFile) {
        String[] cmdargs = {
            MercurialRepository.getCommand(), "import", changesetFile
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
     * @throws AssertFailure if the two entries don't match
     */
    private void assertSameEntries(
            List<HistoryEntry> expected, List<HistoryEntry> actual) {
        assertEquals("Unexpected size", expected.size(), actual.size());
        Iterator<HistoryEntry> actualIt = actual.iterator();
        for (HistoryEntry expectedEntry : expected) {
            assertSameEntry(expectedEntry, actualIt.next());
        }
        assertFalse("More entries than expected", actualIt.hasNext());
    }

    /**
     * Assert that two lists of HistoryEntry objects are equal.
     * @param expected the expected list of entries
     * @param actual the actual list of entries
     * @throws AssertFailure if the two lists don't match
     */
    private void assertSameEntry(HistoryEntry expected, HistoryEntry actual) {
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getRevision(), actual.getRevision());
        assertEquals(expected.getDate(), actual.getDate());
        assertEquals(expected.getMessage(), actual.getMessage());
        assertEquals(expected.getFiles(), actual.getFiles());
    }

    /**
     * Basic tests for the {@code store()} and {@code get()} methods.
     */
    public void testStoreAndGet() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");

        Repository repos = RepositoryFactory.getRepository(reposRoot);

        History historyToStore = repos.getHistory(reposRoot);

        cache.store(historyToStore, repos);
        cache.optimize();

        // test get history for single file

        File makefile = new File(reposRoot, "Makefile");
        assertTrue(makefile.exists());

        History retrievedHistory = cache.get(makefile, repos, true);

        List<HistoryEntry> entries = retrievedHistory.getHistoryEntries();

        assertEquals("Unexpected number of entries", 2, entries.size());

        final String TROND = "Trond Norbye <trond.norbye@sun.com>";

        Iterator<HistoryEntry> entryIt = entries.iterator();

        HistoryEntry e1 = entryIt.next();
        assertEquals(TROND, e1.getAuthor());
        assertEquals("2:585a1b3f2efb", e1.getRevision());
        assertEquals(2, e1.getFiles().size());

        HistoryEntry e2 = entryIt.next();
        assertEquals(TROND, e2.getAuthor());
        assertEquals("1:f24a5fd7a85d", e2.getRevision());
        assertEquals(3, e2.getFiles().size());

        assertFalse(entryIt.hasNext());

        // test get history for directory

        History dirHistory = cache.get(reposRoot, repos, true);
        assertSameEntries(
                historyToStore.getHistoryEntries(),
                dirHistory.getHistoryEntries());

        // test incremental update

        importHgChangeset(
                reposRoot, getClass().getResource("hg-export.txt").getPath());

        repos.createCache(cache, cache.getLatestCachedRevision(repos));
        cache.optimize();

        History updatedHistory = cache.get(reposRoot, repos, true);

        HistoryEntry newEntry = new HistoryEntry(
                "3:78649c3ec6cb",
                new Date(1245446973L / 60 * 60 * 1000), // whole minutes only
                "xyz", "Return failure when executed with no arguments", true);
        newEntry.addFile("/mercurial/main.c");

        LinkedList<HistoryEntry> updatedEntries = new LinkedList<HistoryEntry>(
                updatedHistory.getHistoryEntries());
        assertSameEntry(newEntry, updatedEntries.removeFirst());
        assertSameEntries(historyToStore.getHistoryEntries(), updatedEntries);
    }

    /**
     * Test that {@code getLatestCachedRevision()} returns the correct
     * revision.
     */
    public void testGetLatestCachedRevision() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository repos = RepositoryFactory.getRepository(reposRoot);
        History history = repos.getHistory(reposRoot);
        cache.store(history, repos);
        cache.optimize();

        List<HistoryEntry> entries = history.getHistoryEntries();
        HistoryEntry oldestEntry = entries.get(entries.size() - 1);
        HistoryEntry mostRecentEntry = entries.get(0);

        assertTrue("Unexpected order of history entries",
                oldestEntry.getDate().before(mostRecentEntry.getDate()));

        String latestRevision = mostRecentEntry.getRevision();
        assertNotNull("Unknown latest revision", latestRevision);
        assertEquals("Incorrect latest revision",
                latestRevision, cache.getLatestCachedRevision(repos));

        // test incremental update

        importHgChangeset(
                reposRoot, getClass().getResource("hg-export.txt").getPath());
        repos.createCache(cache, latestRevision);
        assertEquals("3:78649c3ec6cb", cache.getLatestCachedRevision(repos));
    }

    /**
     * Test that {@code hasCacheForDirectory()} works.
     */
    public void testHasCacheForDirectory() throws Exception {
        // Use a Mercurial repository and a Subversion repository in this test.
        File hgRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository hgRepos = RepositoryFactory.getRepository(hgRoot);
        File svnRoot = new File(repositories.getSourceRoot(), "svn");
        Repository svnRepos = RepositoryFactory.getRepository(svnRoot);

        // None of the repositories should have any history.
        assertFalse(cache.hasCacheForDirectory(hgRoot, hgRepos));
        assertFalse(cache.hasCacheForDirectory(svnRoot, svnRepos));

        // Store empty history, so still expect false.
        cache.store(new History(), hgRepos);
        cache.store(new History(), svnRepos);
        assertFalse(cache.hasCacheForDirectory(hgRoot, hgRepos));
        assertFalse(cache.hasCacheForDirectory(svnRoot, svnRepos));

        // Store history for Mercurial repository.
        cache.store(hgRepos.getHistory(hgRoot), hgRepos);
        assertTrue(cache.hasCacheForDirectory(hgRoot, hgRepos));
        assertFalse(cache.hasCacheForDirectory(svnRoot, svnRepos));

        // Store history for Subversion repository.
        cache.store(hgRepos.getHistory(svnRoot), svnRepos);
        assertTrue(cache.hasCacheForDirectory(hgRoot, hgRepos));
        assertTrue(cache.hasCacheForDirectory(svnRoot, svnRepos));
    }

    /**
     * Test that get() is able to continue and return successfully after a lock
     * timeout when accessing the database.
     */
    public void testRetryGetOnTimeout() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository repos = RepositoryFactory.getRepository(reposRoot);
        History history = repos.getHistory(reposRoot);
        cache.store(history, repos);

        // Set the lock timeout to one second to make it go faster.
        final Connection c = DriverManager.getConnection(getURL());
        Statement s = c.createStatement();
        s.execute("call syscs_util.syscs_set_database_property" +
                "('derby.locks.waitTimeout', '1')");

        // Lock one of the tables exclusively in order to block get().
        c.setAutoCommit(false);
        // Originally, we locked the FILECHANGES table here, but that triggered
        // a Derby bug (https://issues.apache.org/jira/browse/DERBY-4330), so
        // now we lock the AUTHORS table instead.
        s.execute("lock table opengrok.authors in exclusive mode");
        s.close();

        // Roll back the transaction in 1.5 seconds so that get() is able to
        // continue after the first timeout.
        final Exception[] ex = new Exception[1];
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500);
                    c.rollback();
                    c.close();
                } catch (Exception e) {
                    ex[0] = e;
                }
            }
        };

        t.start();

        // get() should be able to continue after a timeout.
        assertSameEntries(
                history.getHistoryEntries(),
                cache.get(reposRoot, repos, true).getHistoryEntries());

        t.join();

        // Expose any exception thrown in the helper thread.
        if (ex[0] != null) {
            throw ex[0];
        }
    }

    /**
     * Regression test for bug #11663. If the commit message was longer than
     * the maximum VARCHAR length, a truncation error would be raised by the
     * database. Now the message should be truncated if such a message is
     * encountered.
     */
    public void testVeryLongCommitMessage() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");
        Repository r = RepositoryFactory.getRepository(reposRoot);
        HistoryEntry e0 = new HistoryEntry();
        e0.setAuthor("dummy");
        e0.setDate(new Date());
        e0.addFile("/mercurial/readme.txt");
        e0.setRevision("12:abcdef123456");
        for (int msgLength = 0; msgLength < 40000; msgLength += 48) {
            e0.appendMessage(
                    "this is a line with 48 chars, including newline");
        }

        // Used to get a truncation error from the database here.
        cache.store(new History(Collections.singletonList(e0)), r);

        History h = cache.get(reposRoot, r, false);
        assertEquals("One entry expected", 1, h.getHistoryEntries().size());
        HistoryEntry e1 = h.getHistoryEntries().get(0);
        assertEquals("Author", e0.getAuthor(), e1.getAuthor());
        assertEquals("Date", e0.getDate(), e1.getDate());
        assertEquals("Revision", e0.getRevision(), e1.getRevision());
        assertTrue("No file list requested", e1.getFiles().isEmpty());
        assertFalse("Long message should be truncated",
                e0.getMessage().equals(e1.getMessage()));
        assertEquals("Start of message should be equal to original",
                e0.getMessage().substring(0, 1000),
                e1.getMessage().substring(0, 1000));
    }

    public void testGetInfo() throws HistoryException {
        String info = cache.getInfo();
        assertTrue("Info should contain name of history cache",
                info.startsWith("JDBCHistoryCache"));
        assertTrue("Info should contain driver class",
                info.contains(DERBY_EMBEDDED_DRIVER));
    }

    /**
     * Test that it is possible to store an entry with no author.
     * Bug #11662.
     */
    public void testNullAuthor() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "svn");
        Repository r = RepositoryFactory.getRepository(reposRoot);
        // Create an entry where author is null
        HistoryEntry e = new HistoryEntry(
                "1", new Date(), null, "Initial revision", true);
        e.addFile("/svn/file.txt");
        List<HistoryEntry> entries = Collections.singletonList(e);
        cache.store(new History(entries), r);
        assertSameEntries(
                entries,
                cache.get(reposRoot, r, true).getHistoryEntries());
    }
}
