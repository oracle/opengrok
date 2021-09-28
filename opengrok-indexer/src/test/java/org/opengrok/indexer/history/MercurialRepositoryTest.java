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
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.MERCURIAL;

/**
 * Tests for MercurialRepository.
 */
@EnabledForRepository(MERCURIAL)
public class MercurialRepositoryTest {

    /**
     * Revision numbers present in the Mercurial test repository, in the order
     * they are supposed to be returned from getHistory(), that is latest
     * changeset first.
     */
    private static final String[] REVISIONS = {
            "9:8b340409b3a8",
            "8:6a8c423f5624", "7:db1394c05268", "6:e386b51ddbcc",
            "5:8706402863c6", "4:e494d67af12f", "3:2058725c1470",
            "2:585a1b3f2efb", "1:f24a5fd7a85d", "0:816b6279ae9c"
    };

    // extra revisions for branch test
    private static final String[] REVISIONS_extra_branch = {
            "10:c4518ca0c841"
    };

    // novel.txt (or its ancestors) existed only since revision 3
    private static final String[] REVISIONS_novel = {
            "9:8b340409b3a8",
            "8:6a8c423f5624", "7:db1394c05268", "6:e386b51ddbcc",
            "5:8706402863c6", "4:e494d67af12f", "3:2058725c1470"
    };

    private TestRepository repository;

    /**
     * Set up a test repository. Should be called by the tests that need it. The
     * test repository will be destroyed automatically when the test finishes.
     */
    private void setUpTestRepository() throws IOException {
        repository = new TestRepository();
        repository.create(getClass().getResourceAsStream("repositories.zip"));
    }

    @BeforeEach
    public void setup() throws IOException {
        setUpTestRepository();
    }

    @AfterEach
    public void tearDown() {
        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    @Test
    public void testGetHistory() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(root);
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
    public void testGetHistorySubdir() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");

        // Add a subdirectory with some history.
        runHgCommand(root, "import",
                Paths.get(getClass().getResource("/history/hg-export-subdir.txt").toURI()).toString());

        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(root);
        History hist = mr.getHistory(new File(root, "subdir"));
        List<HistoryEntry> entries = hist.getHistoryEntries();
        assertEquals(1, entries.size());
    }

    /**
     * Test that subset of changesets can be extracted based on penultimate
     * revision number. This works for directories only.
     * @throws Exception
     */
    @Test
    public void testGetHistoryPartial() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(root);
        // Get all but the oldest revision.
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
     * Run Mercurial command.
     * @param reposRoot directory of the repository root
     * @param args {@code hg} command arguments
     */
    public static void runHgCommand(File reposRoot, String... args) {
        List<String> cmdargs = new ArrayList<>();
        MercurialRepository repo = new MercurialRepository();

        cmdargs.add(repo.getRepoCommand());
        cmdargs.addAll(Arrays.asList(args));

        Executor exec = new Executor(cmdargs, reposRoot);
        int exitCode = exec.exec();
        assertEquals(0, exitCode, "hg command '" + cmdargs.toString() + "' failed."
                + "\nexit code: " + exitCode
                + "\nstdout:\n" + exec.getOutputString()
                + "\nstderr:\n" + exec.getErrorString());
    }

    /**
     * Test that history of branched repository contains changesets of the
     * default branch as well.
     * @throws Exception
     */
    @Test
    public void testGetHistoryBranch() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");

        // Branch the repo and add one changeset.
        runHgCommand(root, "unbundle",
                Paths.get(getClass().getResource("/history/hg-branch.bundle").toURI()).toString());
        // Switch to the branch.
        runHgCommand(root, "update", "mybranch");

        // Since the above hg commands change the active branch the repository
        // needs to be initialized here so that its branch matches.
        MercurialRepository mr
                = (MercurialRepository) RepositoryFactory.getRepository(root);

        // Get all revisions.
        History hist = mr.getHistory(root);
        List<HistoryEntry> entries = hist.getHistoryEntries();
        List<String> both = new ArrayList<>(REVISIONS.length
                + REVISIONS_extra_branch.length);
        Collections.addAll(both, REVISIONS_extra_branch);
        Collections.addAll(both, REVISIONS);
        String[] revs = both.toArray(new String[0]);
        assertEquals(revs.length, entries.size());
        // Ideally we should check that the last revision is branched but
        // there is currently no provision for that in HistoryEntry object.
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry e = entries.get(i);
            assertEquals(revs[i], e.getRevision());
            assertNotNull(e.getAuthor());
            assertNotNull(e.getDate());
            assertNotNull(e.getFiles());
            assertNotNull(e.getMessage());
        }

        // Get revisions starting with given changeset before the repo was branched.
        hist = mr.getHistory(root, "8:6a8c423f5624");
        entries = hist.getHistoryEntries();
        assertEquals(2, entries.size());
        assertEquals(REVISIONS_extra_branch[0], entries.get(0).getRevision());
        assertEquals(REVISIONS[0], entries.get(1).getRevision());
    }

    /**
     * Test that contents of last revision of a text file match expected content.
     * @throws java.lang.Exception
     */
    @Test
    public void testGetHistoryGet() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(root);
        String exp_str = "This will be a first novel of mine.\n"
                + "\n"
                + "Chapter 1.\n"
                + "\n"
                + "Let's write some words. It began like this:\n"
                + "\n"
                + "...\n";
        byte[] buffer = new byte[1024];

        InputStream input = mr.getHistoryGet(root.getCanonicalPath(),
                "novel.txt", REVISIONS[0]);
        assertNotNull(input);

        String str = "";
        int len;
        while ((len = input.read(buffer)) > 0) {
            str += new String(buffer, 0, len);
        }
        assertNotSame(str.length(), 0);
        assertEquals(exp_str, str);
    }

    /**
     * Test that it is possible to get contents of multiple revisions of a file.
     * @throws java.lang.Exception
     */
    @Test
    public void testgetHistoryGetForAll() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(root);

        for (String rev : REVISIONS_novel) {
            InputStream input = mr.getHistoryGet(root.getCanonicalPath(),
                    "novel.txt", rev);
            assertNotNull(input);
        }
    }

    /**
     * Test that {@code getHistoryGet()} returns historical contents of renamed
     * file.
     * @throws java.lang.Exception
     */
    @Test
    public void testGetHistoryGetRenamed() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(root);
        String exp_str = "This is totally plaintext file.\n";
        byte[] buffer = new byte[1024];

        /*
         * In our test repository the file was renamed twice since
         * revision 3.
         */
        InputStream input = mr.getHistoryGet(root.getCanonicalPath(),
                "novel.txt", "3");
        assert (input != null);
        int len = input.read(buffer);
        assert (len != -1);
        String str = new String(buffer, 0, len);
        assert (str.compareTo(exp_str) == 0);
    }

    /**
     * Test that {@code getHistory()} throws an exception if the revision
     * argument doesn't match any of the revisions in the history.
     * @throws java.lang.Exception
     */
    @Test
    public void testGetHistoryWithNoSuchRevision() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(root);

        // Get the sequence number and the hash from one of the revisions.
        String[] revisionParts = REVISIONS[1].split(":");
        assertEquals(2, revisionParts.length);
        int number = Integer.parseInt(revisionParts[0]);
        String hash = revisionParts[1];

        // Construct a revision identifier that doesn't exist.
        String constructedRevision = (number + 1) + ":" + hash;
        assertThrows(HistoryException.class, () -> mr.getHistory(root, constructedRevision));
    }

    @Test
    void testGetHistorySinceTillNullNull() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(root);
        History history = hgRepo.getHistory(root, null, null);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(10, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(REVISIONS), revisions);
    }

    @Test
    void testGetHistorySinceTillNullRev() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(root);
        History history = hgRepo.getHistory(root, null, REVISIONS[4]);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(6, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(Arrays.copyOfRange(REVISIONS, 4, REVISIONS.length)), revisions);
    }

    @Test
    void testGetHistorySinceTillRevNull() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(root);
        History history = hgRepo.getHistory(root, REVISIONS[3], null);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(3, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(Arrays.copyOfRange(REVISIONS, 0, 3)), revisions);
    }

    @Test
    void testGetHistorySinceTillRevRev() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(root);
        History history = hgRepo.getHistory(root, REVISIONS[7], REVISIONS[2]);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(5, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(Arrays.copyOfRange(REVISIONS, 2, 7)), revisions);
    }

    @Test
    void testGetHistoryRenamedFileTillRev() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(true);
        File root = new File(repository.getSourceRoot(), "mercurial");
        File file = new File(root, "novel.txt");
        assertTrue(file.exists() && file.isFile());
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(root);
        History history = hgRepo.getHistory(file, null, "7:db1394c05268");
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(5, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(Arrays.copyOfRange(REVISIONS, 2, 7)), revisions);
    }

    @Test
    void testGetLastHistoryEntry() throws Exception {
        File root = new File(repository.getSourceRoot(), "mercurial");
        File file = new File(root, "novel.txt");
        assertTrue(file.exists() && file.isFile());
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(root);
        HistoryEntry historyEntry = hgRepo.getLastHistoryEntry(file, true);
        assertNotNull(historyEntry);
        assertEquals("8:6a8c423f5624", historyEntry.getRevision());
    }
}
