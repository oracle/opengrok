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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
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
     * Basic tests for the {@code store()} and {@code get()} methods.
     */
    public void testStoreAndGet() throws Exception {
        File reposRoot = new File(repositories.getSourceRoot(), "mercurial");

        Repository repos = RepositoryFactory.getRepository(reposRoot);

        History historyToStore = repos.getHistory(reposRoot);

        cache.store(historyToStore, repos);

        File makefile = new File(reposRoot, "Makefile");
        assertTrue(makefile.exists());

        History retrievedHistory = cache.get(makefile, repos);

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

        List<HistoryEntry> entries = history.getHistoryEntries();
        HistoryEntry oldestEntry = entries.get(entries.size() - 1);
        HistoryEntry mostRecentEntry = entries.get(0);

        assertTrue("Unexpected order of history entries",
                oldestEntry.getDate().before(mostRecentEntry.getDate()));

        String latestRevision = mostRecentEntry.getRevision();
        assertNotNull("Unknown latest revision", latestRevision);
        assertEquals("Incorrect latest revision",
                latestRevision, cache.getLatestCachedRevision(repos));
    }
}
