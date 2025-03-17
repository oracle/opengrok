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
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private File repositoryRoot;

    /**
     * Set up a test repository. Should be called by the tests that need it. The
     * test repository will be destroyed automatically when the test finishes.
     */
    private void setUpTestRepository() throws IOException, URISyntaxException {
        repository = new TestRepository();
        repository.create(Objects.requireNonNull(getClass().getResource("/repositories")));
        repositoryRoot = new File(repository.getSourceRoot(), "mercurial");
    }

    @BeforeEach
    void setup() throws IOException, URISyntaxException {
        setUpTestRepository();
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    @Test
    void testGetHistory() throws Exception {
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
    void testGetHistorySubdir() throws Exception {
        // Add a subdirectory with some history.
        runHgCommand(repositoryRoot, "import",
                Paths.get(getClass().getResource("/history/hg-export-subdir.txt").toURI()).toString());

        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        History hist = mr.getHistory(new File(repositoryRoot, "subdir"));
        List<HistoryEntry> entries = hist.getHistoryEntries();
        assertEquals(1, entries.size());
    }

    /**
     * Test that subset of changesets can be extracted based on penultimate
     * revision number. This works for directories only.
     */
    @Test
    void testGetHistoryPartial() throws Exception {
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        // Get all but the oldest revision.
        History hist = mr.getHistory(repositoryRoot, REVISIONS[REVISIONS.length - 1]);
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
        assertEquals(0, exitCode, "hg command '" + cmdargs + "' failed."
                + "\nexit code: " + exitCode
                + "\nstdout:\n" + exec.getOutputString()
                + "\nstderr:\n" + exec.getErrorString());
    }

    /**
     * Test that history of branched repository contains changesets of the
     * default branch as well.
     */
    @Test
    void testGetHistoryBranch() throws Exception {
        // Branch the repo and add one changeset.
        runHgCommand(repositoryRoot, "unbundle",
                Paths.get(getClass().getResource("/history/hg-branch.bundle").toURI()).toString());
        // Switch to the branch.
        runHgCommand(repositoryRoot, "update", "mybranch");

        // Since the above hg commands change the active branch the repository
        // needs to be initialized here so that its branch matches.
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);

        // Get all revisions.
        History hist = mr.getHistory(repositoryRoot);
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
        hist = mr.getHistory(repositoryRoot, "8:6a8c423f5624");
        entries = hist.getHistoryEntries();
        assertEquals(2, entries.size());
        assertEquals(REVISIONS_extra_branch[0], entries.get(0).getRevision());
        assertEquals(REVISIONS[0], entries.get(1).getRevision());
    }

    /**
     * Test that contents of last revision of a text file match expected content.
     */
    @Test
    void testGetHistoryGet() throws Exception {
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        String exp_str = "This will be a first novel of mine.\n"
                + "\n"
                + "Chapter 1.\n"
                + "\n"
                + "Let's write some words. It began like this:\n"
                + "\n"
                + "...\n";
        byte[] buffer = new byte[1024];

        InputStream input = mr.getHistoryGet(repositoryRoot.getCanonicalPath(),
                "novel.txt", REVISIONS[0]);
        assertNotNull(input);

        String str = "";
        int len;
        while ((len = input.read(buffer)) > 0) {
            str += new String(buffer, 0, len);
        }
        assertNotSame(0, str.length());
        assertEquals(exp_str, str);
    }

    /**
     * Test that it is possible to get contents of multiple revisions of a file.
     */
    @Test
    void testGetHistoryGetForAll() throws Exception {
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);

        for (String rev : REVISIONS_novel) {
            InputStream input = mr.getHistoryGet(repositoryRoot.getCanonicalPath(),
                    "novel.txt", rev);
            assertNotNull(input);
        }
    }

    /**
     * Test that {@code getHistoryGet()} returns historical contents of renamed file.
     */
    @Test
    void testGetHistoryGetRenamed() throws Exception {
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        String exp_str = "This is totally plaintext file.\n";
        byte[] buffer = new byte[1024];

        /*
         * In our test repository the file was renamed twice since
         * revision 3.
         */
        InputStream input = mr.getHistoryGet(repositoryRoot.getCanonicalPath(),
                "novel.txt", "3");
        assertNotNull(input);
        int len = input.read(buffer);
        assertNotEquals(-1, len );
        String str = new String(buffer, 0, len);
        assertEquals(0, str.compareTo(exp_str));
    }

    /**
     * Test that {@code getHistory()} throws an exception if the revision
     * argument doesn't match any of the revisions in the history.
     */
    @Test
    void testGetHistoryWithNoSuchRevision() throws Exception {
        MercurialRepository mr = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);

        // Get the sequence number and the hash from one of the revisions.
        String[] revisionParts = REVISIONS[1].split(":");
        assertEquals(2, revisionParts.length);
        int number = Integer.parseInt(revisionParts[0]);
        String hash = revisionParts[1];

        // Construct a revision identifier that doesn't exist.
        String constructedRevision = (number + 1) + ":" + hash;
        assertThrows(HistoryException.class, () -> mr.getHistory(repositoryRoot, constructedRevision));
    }

    @Test
    void testGetHistorySinceTillNullNull() throws Exception {
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        History history = hgRepo.getHistory(repositoryRoot, null, null);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(10, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(REVISIONS), revisions);
    }

    @Test
    void testGetHistorySinceTillNullRev() throws Exception {
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        History history = hgRepo.getHistory(repositoryRoot, null, REVISIONS[4]);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(6, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(Arrays.copyOfRange(REVISIONS, 4, REVISIONS.length)), revisions);
    }

    @Test
    void testGetHistorySinceTillRevNull() throws Exception {
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        History history = hgRepo.getHistory(repositoryRoot, REVISIONS[3], null);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(3, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(Arrays.copyOfRange(REVISIONS, 0, 3)), revisions);
    }

    @Test
    void testGetHistorySinceTillRevRev() throws Exception {
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        History history = hgRepo.getHistory(repositoryRoot, REVISIONS[7], REVISIONS[2]);
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
        File file = new File(repositoryRoot, "novel.txt");
        assertTrue(file.exists() && file.isFile());
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
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
        File file = new File(repositoryRoot, "novel.txt");
        assertTrue(file.exists() && file.isFile());
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        HistoryEntry historyEntry = hgRepo.getLastHistoryEntry(file, true);
        assertNotNull(historyEntry);
        assertEquals("8:6a8c423f5624", historyEntry.getRevision());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testMergeCommits(boolean isMergeCommitsEnabled) throws Exception {
        // The bundle will add a branch and merge commit in the default branch.
        runHgCommand(repositoryRoot, "unbundle",
                Paths.get(getClass().getResource("/history/hg-merge.bundle").toURI()).toString());
        runHgCommand(repositoryRoot, "update");

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        boolean isMergeCommitsEnabledOrig = env.isMergeCommitsEnabled();
        env.setMergeCommitsEnabled(isMergeCommitsEnabled);

        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        History history = hgRepo.getHistory(repositoryRoot, null);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        if (isMergeCommitsEnabled) {
            assertEquals(12, history.getHistoryEntries().size());
            assertNotNull(history.getLastHistoryEntry());
            assertEquals("merge", history.getLastHistoryEntry().getMessage());
        } else {
            assertEquals(11, history.getHistoryEntries().size());
        }

        // Cleanup.
        env.setMergeCommitsEnabled(isMergeCommitsEnabledOrig);
    }

    @Test
    void testAnnotationNegative() throws Exception {
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        File file = new File(repositoryRoot, "nonexistent");
        assertFalse(file.exists());
        Annotation annotation = hgRepo.annotate(file, null);
        assertNull(annotation);
    }

    private static Stream<Pair<String, List<String>>> provideParametersForPositiveAnnotationTest() {
        return Stream.of(Pair.of("8:6a8c423f5624", List.of("7", "8")),
                Pair.of("7:db1394c05268", List.of("7")));
    }

    @ParameterizedTest
    @MethodSource("provideParametersForPositiveAnnotationTest")
    void testAnnotationPositive(Pair<String, List<String>> pair) throws Exception {
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        assertNotNull(hgRepo);
        File file = new File(repositoryRoot, "novel.txt");
        assertTrue(file.exists());
        // The annotate() method calls uses HistoryGuru's getHistory() method which requires the RepositoryLookup
        // to be initialized. Do so via setRepositories().
        RuntimeEnvironment.getInstance().setRepositories(repository.getSourceRoot());
        Annotation annotation = hgRepo.annotate(file, pair.getKey());
        assertNotNull(annotation);
        List<String> revisions = new ArrayList<>(annotation.getRevisions());
        assertEquals(pair.getValue(), revisions);
    }

    /**
     * Test special case of repository with no tags, in this case empty repository.
     */
    @Test
    void testBuildTagListEmpty() throws Exception {
        Path repositoryRootPath = Files.createDirectory(Path.of(RuntimeEnvironment.getInstance().getSourceRootPath(),
                "emptyTagsTest"));
        File repositoryRoot = repositoryRootPath.toFile();
        assertTrue(repositoryRoot.isDirectory());
        runHgCommand(repositoryRoot, "init");
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        assertNotNull(hgRepo);
        hgRepo.buildTagList(new File(hgRepo.getDirectoryName()), CommandTimeoutType.INDEXER);
        assertEquals(0, hgRepo.getTagList().size());
        IOUtils.removeRecursive(repositoryRootPath);
    }

    /**
     * Extract the tags from the original repository. It already contains one tag.
     */
    @Test
    void testBuildTagListInitial() throws Exception {
        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        assertNotNull(hgRepo);
        hgRepo.buildTagList(new File(hgRepo.getDirectoryName()), CommandTimeoutType.INDEXER);
        var tags = hgRepo.getTagList();
        assertEquals(1, tags.size());
        Set<TagEntry> expectedTags = new TreeSet<>();
        TagEntry tagEntry = new MercurialTagEntry(7, "start_of_novel");
        expectedTags.add(tagEntry);
        assertEquals(expectedTags, tags);
    }

    /**
     * Clone the original repository, create branch and add tag to the branch, switch back to the original branch,
     * add new tag, check that the extracted tags contain the pre-existing and new one
     * but not the non-default branch tag.
     */
    @Test
    void testBuildTagListOneMore() throws Exception {
        Path repositoryRootPath = Files.createDirectory(Path.of(RuntimeEnvironment.getInstance().getSourceRootPath(),
                "addedTagTest"));
        File repositoryRoot = repositoryRootPath.toFile();
        // Clone the internal repository because it will be modified.
        // This avoids interference with other tests in this class.
        runHgCommand(this.repositoryRoot, "clone", this.repositoryRoot.toString(), repositoryRootPath.toString());

        // Branch the repo and add one changeset.
        runHgCommand(repositoryRoot, "unbundle",
                Paths.get(getClass().getResource("/history/hg-branch.bundle").toURI()).toString());
        // Switch to the branch.
        runHgCommand(repositoryRoot, "update", "mybranch");
        final String branchTagName = "branch_tag";
        runHgCommand(repositoryRoot, "tag", branchTagName);

        // Switch back to the default branch.
        runHgCommand(repositoryRoot, "update", "default");

        MercurialRepository hgRepo = (MercurialRepository) RepositoryFactory.getRepository(repositoryRoot);
        assertNotNull(hgRepo);
        // Using double space on purpose to test the parsing of tags.
        final String newTagName = "foo  bar";
        runHgCommand(repositoryRoot, "tag", newTagName);
        hgRepo.buildTagList(new File(hgRepo.getDirectoryName()), CommandTimeoutType.INDEXER);
        var tags = hgRepo.getTagList();
        assertNotNull(tags);
        assertEquals(2, tags.size());
        // TagEntry has special semantics for comparing/equality which does not compare the tags at all,
        // so using assertEquals() on two sets of TagEntry objects would not help.
        // Instead, check the tags separately.
        assertEquals(List.of(7, 9), tags.stream().map(TagEntry::getRevision).collect(Collectors.toList()));
        List<String> expectedTags = List.of("start_of_novel", newTagName);
        assertEquals(expectedTags, tags.stream().map(TagEntry::getTags).collect(Collectors.toList()));
        IOUtils.removeRecursive(repositoryRootPath);
    }
}
