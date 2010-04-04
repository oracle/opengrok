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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Tests for MercurialRepository.
 */
public class MercurialRepositoryTest {

    /**
     * Revision numbers present in the Mercurial test repository, in the
     * order they are supposed to be returned from getHistory().
     */
    private final static String[] REVISIONS = {
        "2:585a1b3f2efb", "1:f24a5fd7a85d", "0:816b6279ae9c"
    };

    private TestRepository repository;

    /**
     * Set up a test repository. Should be called by the tests that need it.
     * The test repository will be destroyed automatically when the test
     * finishes.
     */
    private void setUpTestRepository() throws IOException {
        repository = new TestRepository();
        repository.create(getClass().getResourceAsStream("repositories.zip"));
    }

    @After
    public void tearDown() {
        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    @Test
    public void testGetHistory() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr =
                (MercurialRepository) RepositoryFactory.getRepository(root);
        History hist = mr.getHistory(root);
        List<HistoryEntry> entries = hist.getHistoryEntries();
        assertEquals(REVISIONS.length, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry e = entries.get(i);
            assertEquals(REVISIONS[i], e.getRevision());
            assertNotNull(e.getAuthor());
            assertNotNull(e.getDate());
            assertNotNull(e.getFiles());
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testGetHistoryPartial() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr =
                (MercurialRepository) RepositoryFactory.getRepository(root);
        // Get all but the oldest revision
        History hist = mr.getHistory(root, REVISIONS[REVISIONS.length - 1]);
        List<HistoryEntry> entries = hist.getHistoryEntries();
        assertEquals(REVISIONS.length - 1, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry e = entries.get(i);
            assertEquals(REVISIONS[i], e.getRevision());
            assertNotNull(e.getAuthor());
            assertNotNull(e.getDate());
            assertNotNull(e.getFiles());
            assertNotNull(e.getMessage());
        }
    }

    /**
     * Test that {@code getHistory()} throws an exception if the revision
     * argument doesn't match any of the revisions in the history.
     */
    @Test
    public void testGetHistoryWithNoSuchRevision() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr =
                (MercurialRepository) RepositoryFactory.getRepository(root);

        // Get the sequence number and the hash from one of the revisions.
        String[] revisionParts = REVISIONS[1].split(":");
        assertEquals(2, revisionParts.length);
        int number = Integer.parseInt(revisionParts[0]);
        String hash = revisionParts[1];

        // Construct a revision identifier that doesn't exist.
        String constructedRevision = (number + 1) + ":" + hash;
        try {
            mr.getHistory(root, constructedRevision);
            fail("getHistory() should have failed");
        } catch (HistoryException he) {
            String msg = he.getMessage();
            if (msg != null && msg.contains("not found in the repository")) {
                // expected exception, do nothing
            } else {
                // unexpected exception, rethrow it
                throw he;
            }
        }
    }

}
