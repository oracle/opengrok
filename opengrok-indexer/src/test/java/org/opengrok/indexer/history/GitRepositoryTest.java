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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author austvik
 */
public class GitRepositoryTest {

    private static TestRepository repository = new TestRepository();
    private GitRepository instance;

    @BeforeAll
    public static void setUpClass() throws IOException, URISyntaxException {
        repository.create(GitRepositoryTest.class.getResource("/repositories"));
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
        repository = null;
    }

    @BeforeEach
    public void setUp() {
        instance = new GitRepository();
    }

    @AfterEach
    public void tearDown() {
        instance = null;
    }

    private void checkCurrentVersion(File root, int timestamp, String commitId, String shortComment)
            throws Exception {
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        assertNotNull(gitrepo);
        String ver = gitrepo.determineCurrentVersion();
        assertNotNull(ver);
        Date date = new Date((long) (timestamp) * 1000);
        assertEquals(Repository.format(date) + " " + commitId + " " + shortComment, ver);
    }

    @Test
    void testDetermineCurrentVersionWithEmptyRepository() throws Exception {
        File emptyGitDir = new File(repository.getSourceRoot(), "gitEmpty");
        try (Git git = Git.init().setDirectory(emptyGitDir).call()) {
            GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(git.getRepository().getWorkTree());
            assertNotNull(gitrepo);
            String ver = gitrepo.determineCurrentVersion();
            assertNull(ver);
            removeRecursive(emptyGitDir);
        }
    }

    @Test
    void testDetermineCurrentVersionOfKnownRepository() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        assertNotNull(gitrepo);
        String ver = gitrepo.determineCurrentVersion();
        assertNotNull(ver);
        Date date = new Date((long) (1485438707) * 1000);
        assertEquals(Repository.format(date) + " 84599b3 Kryštof Tulinger renaming directories", ver);
    }

    @Test
    void testDetermineCurrentVersionAfterChange() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        assertNotNull(gitrepo);
        String ver;
        // Clone under source root to avoid problems with prohibited symlinks.
        File localPath = new File(repository.getSourceRoot(), "gitCloneTestCurrentVersion");
        String cloneUrl = root.toURI().toString();
        try (Git gitClone = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(localPath)
                .call()) {
            File cloneRoot = gitClone.getRepository().getWorkTree();
            // Check the checkout went okay.
            checkCurrentVersion(cloneRoot, 1485438707, "84599b3",
                    "Kryštof Tulinger renaming directories");

            // Create new file, commit and check the current version string.
            File myFile = new File(cloneRoot, "testfile");
            if (!myFile.createNewFile()) {
                throw new IOException("Could not create file " + myFile);
            }
            gitClone.add()
                    .addFilepattern("testfile")
                    .call();
            String comment = "Added testfile";
            String authorName = "Foo Bar";
            gitClone.commit()
                    .setMessage(comment)
                    .setAuthor(authorName, "foo@bar.com")
                    .call();

            gitrepo = (GitRepository) RepositoryFactory.getRepository(cloneRoot);
            assertNotNull(gitrepo);
            ver = gitrepo.determineCurrentVersion();
            assertNotNull(ver);
            // Not able to set commit ID and date so only check the rest.
            assertTrue(ver.endsWith(authorName + " " + comment), "ends with author and commit comment");

            removeRecursive(cloneRoot);
        }
    }

    @Test
    void testDetermineBranchBasic() throws Exception {
        // First check branch of known repository.
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        String branch = gitrepo.determineBranch();
        assertNotNull(branch);
        assertEquals("master", branch);
    }

    @Test
    void testDetermineBranchAfterChange() throws Exception {
        // Clone the test repository and create new branch there.
        // Clone under source root to avoid problems with prohibited symlinks.
        File root = new File(repository.getSourceRoot(), "git");
        File localPath = new File(repository.getSourceRoot(), "gitCloneTestDetermineBranch");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        String branch;
        String cloneUrl = root.toURI().toString();
        try (Git gitClone = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(localPath)
                .call()) {

            Ref ref = gitClone.checkout().setCreateBranch(true).setName("foo").call();
            assertNotNull(ref);

            File cloneRoot = gitClone.getRepository().getWorkTree();
            gitrepo = (GitRepository) RepositoryFactory.getRepository(cloneRoot);
            branch = gitrepo.determineBranch();
            assertNotNull(branch);
            assertEquals("foo", branch);

            removeRecursive(cloneRoot);
        }
    }

    @Test
    void testGetHistoryInBranch() throws Exception {
        // Clone the test repository and create new branch there.
        // Clone under source root to avoid problems with prohibited symlinks.
        File root = new File(repository.getSourceRoot(), "git");
        File localPath = new File(repository.getSourceRoot(), "testGetHistoryInBranch");
        String cloneUrl = root.toURI().toString();
        try (Git gitClone = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(localPath)
                .call()) {

            Ref ref = gitClone.checkout().setCreateBranch(true).setName("foo").call();
            assertNotNull(ref);

            File cloneRoot = gitClone.getRepository().getWorkTree();
            GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(cloneRoot);

            History history = gitrepo.getHistory(cloneRoot);
            assertNotNull(history);
            int numEntries = history.getHistoryEntries().size();
            assertTrue(numEntries > 0);

            RevCommit commit = gitClone.commit().
                    setAuthor("Snufkin", "snufkin@moomin.valley").
                    setMessage("fresh commit on a new branch").setAllowEmpty(true).call();
            assertNotNull(commit);

            history = gitrepo.getHistory(cloneRoot);
            assertEquals(numEntries + 1, history.getHistoryEntries().size());

            removeRecursive(cloneRoot);
        }
    }

    @Test
    void testDetermineParentEmpty() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        String parent = gitrepo.determineParent();
        assertNull(parent);
    }

    @Test
    void testDetermineParent() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        String parent;
        // Clone the repository and create new origin there.
        // Clone under source root to avoid problems with prohibited symlinks.
        File localPath = new File(repository.getSourceRoot(), "gitCloneTestDetermineParent");
        String cloneUrl = root.toURI().toString();
        try (Git gitClone = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(localPath)
                .call()) {

            String uri = "http://foo.bar";
            gitClone.remoteAdd().setName("origin").setUri(new URIish(uri)).call();

            File cloneRoot = gitClone.getRepository().getWorkTree();
            gitrepo = (GitRepository) RepositoryFactory.getRepository(cloneRoot);
            parent = gitrepo.determineParent();
            assertNotNull(parent);
            assertEquals(uri, parent);

            removeRecursive(cloneRoot);
        }
    }

    /**
     * Test of fileHasAnnotation method, of class GitRepository.
     */
    @Test
    void fileHasAnnotation() {
        boolean result = instance.fileHasAnnotation(null);
        assertTrue(result);
    }

    /**
     * Test of fileHasHistory method, of class GitRepository.
     */
    @Test
    void fileHasHistory() {
        boolean result = instance.fileHasHistory(null);
        assertTrue(result);
    }

    /**
     * For the following renamed tests the structure in the git repo is as following:
     * <pre>
     *     ce4c98ec - new file renamed.c (with some content)
     *     b6413947 - renamed.c renamed to moved/renamed.c
     *     1086eaf5 - modification of file moved/renamed.c (different content)
     *     67dfbe26 - moved/renamed.c renamed to moved/renamed2.c
     *     84599b3c - moved/renamed2.c renamed to moved2/renamed2.c
     * </pre>
     */
    @Test
    void testRenamedFiles() throws Exception {
        String[][] tests = new String[][] {
                {Paths.get("moved2", "renamed2.c").toString(), "84599b3c", Paths.get("moved2", "renamed2.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), "67dfbe26", Paths.get("moved", "renamed2.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), "67dfbe26", Paths.get("moved", "renamed2.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), "1086eaf5", Paths.get("moved", "renamed.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), "b6413947", Paths.get("moved", "renamed.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), "ce4c98ec", "renamed.c"},
                {Paths.get("moved2", "renamed2.c").toString(), "bb74b7e8", "renamed.c"}
        };

        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.setHandleRenamedFiles(true);

        for (String[] test : tests) {
            String file = Paths.get(root.getCanonicalPath(), test[0]).toString();
            String changeset = test[1];
            String expectedName = test[2];

            String originalName = gitrepo.findOriginalName(file, changeset);
            assertEquals(expectedName, originalName);
        }
    }

    private void testAnnotationOfFile(GitRepository gitrepo, File file, String revision, Set<String> revSet) throws Exception {
        Annotation annotation = gitrepo.annotate(file, revision);

        assertNotNull(annotation);
        assertEquals(revSet, annotation.getRevisions());
    }

    @Test
    void testAnnotationOfRenamedFileWithHandlingOff() throws Exception {
        String[] revisions = {"84599b3c"};
        Set<String> revSet = new HashSet<>();
        Collections.addAll(revSet, revisions);

        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.setHandleRenamedFiles(false);
        File renamedFile = Paths.get(root.getAbsolutePath(), "moved2", "renamed2.c").toFile();
        testAnnotationOfFile(gitrepo, renamedFile, null, revSet);
    }

    @Test
    void testAnnotationOfRenamedFileWithHandlingOn() throws Exception {
        String[] revisions = {"1086eaf5", "ce4c98ec"};
        Set<String> revSet = new HashSet<>();
        Collections.addAll(revSet, revisions);

        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.setHandleRenamedFiles(true);
        File renamedFile = Paths.get(root.getAbsolutePath(), "moved2", "renamed2.c").toFile();
        testAnnotationOfFile(gitrepo, renamedFile, null, revSet);
    }

    @Test
    void testAnnotationOfRenamedFilePastWithHandlingOn() throws Exception {
        String[] revisions = {"1086eaf5", "ce4c98ec"};
        Set<String> revSet = new HashSet<>();
        Collections.addAll(revSet, revisions);

        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.setHandleRenamedFiles(true);
        File renamedFile = Paths.get(root.getAbsolutePath(), "moved2", "renamed2.c").toFile();
        testAnnotationOfFile(gitrepo, renamedFile, "1086eaf5", revSet);
    }

    @Test
    void testInvalidRenamedFiles() throws Exception {
        String[][] tests = new String[][] {
                {"", "67dfbe26"},
                {"moved/renamed2.c", ""},
                {"", ""},
                {null, "67dfbe26"},
                {"moved/renamed2.c", null}

        };
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitRepository = (GitRepository) RepositoryFactory.getRepository(root);
        assertThrows(IOException.class, () -> {
            for (String[] test : tests) {
                String file = test[0];
                String changeset = test[1];
                gitRepository.findOriginalName(file, changeset);
            }
        });
    }

    /**
     * Test that {@code getHistoryGet()} returns historical contents of renamed
     * file.
     * @see #testRenamedFiles for git repo structure info
     */
    @Test
    void testGetRenamedFileContent() throws Exception {
        String old_content
                = "#include <stdio.h>\n"
                + "#include <stdlib.h>\n"
                + "\n"
                + "int main ( int argc, const char * argv[] )\n"
                + "{\n"
                + "\tint i;\n"
                + "\tfor ( i = 1; i < argc; i ++ )\n"
                + "\t{\n"
                + "\t\tprintf ( \"%s called with %d\\n\", argv [ 0 ], argv [ i ] );\n"
                + "\t}\n"
                + "\n"
                + "\treturn 0;\n"
                + "}\n";

        String new_content
                = "#include <stdio.h>\n"
                + "#include <stdlib.h>\n"
                + "\n"
                + "int foo ( const char * path )\n"
                + "{\n"
                + "\treturn path && *path == 'A';\n"
                + "}\n"
                + "\n"
                + "int main ( int argc, const char * argv[] )\n"
                + "{\n"
                + "\tint i;\n"
                + "\tfor ( i = 1; i < argc; i ++ )\n"
                + "\t{\n"
                + "\t\tprintf ( \"%s called with %d\\n\", argv [ 0 ], argv [ i ] );\n"
                + "\t}\n"
                + "\n"
                + "\tprintf ( \"Hello, world!\\n\" );\n"
                + "\n"
                + "\tif ( foo ( argv [ 0 ] ) )\n"
                + "\t{\n"
                + "\t\tprintf ( \"Correct call\\n\" );\n"
                + "\t}\n"
                + "\n"
                + "\treturn 0;\n"
                + "}\n";

        final List<String[]> tests = Arrays.asList(
                // old content (after revision 1086eaf5 inclusively)
                new String[] {Paths.get("moved2", "renamed2.c").toString(), "84599b3c", new_content},
                new String[] {Paths.get("moved2", "renamed2.c").toString(), "67dfbe26", new_content},
                new String[] {Paths.get("moved2", "renamed2.c").toString(), "1086eaf5", new_content},

                new String[] {Paths.get("moved", "renamed2.c").toString(), "67dfbe26", new_content},
                new String[] {Paths.get("moved", "renamed2.c").toString(), "1086eaf5", new_content},

                new String[] {Paths.get("moved", "renamed.c").toString(), "1086eaf5", new_content},

                // old content (before revision b6413947 inclusively)
                new String[] {Paths.get("moved2", "renamed2.c").toString(), "b6413947", old_content},
                new String[] {Paths.get("moved2", "renamed2.c").toString(), "ce4c98ec", old_content},
                new String[] {Paths.get("moved", "renamed2.c").toString(), "b6413947", old_content},
                new String[] {Paths.get("moved", "renamed2.c").toString(), "ce4c98ec", old_content},
                new String[] {Paths.get("moved", "renamed.c").toString(), "b6413947", old_content},
                new String[] {Paths.get("moved", "renamed.c").toString(), "ce4c98ec", old_content},
                new String[] {Paths.get("renamed.c").toString(), "ce4c98ec", old_content}
        );

        for (String[] params : tests) {
            runRenamedTest(params[0], params[1], params[2]);
        }
    }

    /**
     * Test that {@code getHistoryGet()} returns historical contents of renamed
     * file.
     * @see #testRenamedFiles for git repo structure info
     */
    @Test
    void testGetHistoryForNonExistentRenamed() throws Exception {
        final List<String[]> tests = Arrays.asList(
                new String[] {Paths.get("moved", "renamed2.c").toString(), "84599b3c"},

                new String[] {Paths.get("moved", "renamed.c").toString(), "84599b3c"},
                new String[] {Paths.get("moved", "renamed.c").toString(), "67dfbe26"},

                new String[] {Paths.get("renamed.c").toString(), "84599b3c"},
                new String[] {Paths.get("renamed.c").toString(), "67dfbe26"},
                new String[] {Paths.get("renamed.c").toString(), "1086eaf5"},
                new String[] {Paths.get("renamed.c").toString(), "b6413947"}
        );

        for (String[] params : tests) {
            runRenamedTest(params[0], params[1], null);
        }
    }

    private void runRenamedTest(String fname, String cset, String content) throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        byte[] buffer = new byte[4096];

        InputStream input = gitrepo.getHistoryGet(root.getCanonicalPath(), fname, cset);
        if (content == null) {
            assertNull(input, String.format("Expecting the revision %s for file %s does not exist", cset, fname));
        } else {
            assertNotNull(input, String.format("Expecting the revision %s for file %s does exist", cset, fname));
            int len = input.read(buffer);
            assertNotEquals(-1, len, String.format("Expecting the revision %s for file %s does have some content", cset, fname));
            String str = new String(buffer, 0, len);
            assertEquals(content, str, String.format("Expecting the revision %s for file %s does match the expected content", cset, fname));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testHistory(boolean renamedHandling) throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(renamedHandling);
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        List<HistoryEntry> entries = List.of(
                new HistoryEntry("84599b3c", new Date(1485438707000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "    renaming directories\n\n", true,
                        Set.of(File.separator + Paths.get("git", "moved2", "renamed2.c"))),
                new HistoryEntry("67dfbe26", new Date(1485263397000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "    renaming renamed -> renamed2\n\n", true,
                        Set.of(File.separator + Paths.get("git", "moved", "renamed2.c"))),
                new HistoryEntry("1086eaf5", new Date(1485263368000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "     adding some lines into renamed.c\n\n", true,
                        Set.of(File.separator + Paths.get("git", "moved", "renamed.c"))),
                new HistoryEntry("b6413947", new Date(1485263264000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "    moved renamed.c to new location\n\n", true,
                        Set.of(File.separator + Paths.get("git", "moved", "renamed.c"))),
                new HistoryEntry("ce4c98ec", new Date(1485263232000L),  // start in the sub-test below
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "    adding simple file for renamed file testing\n\n", true,
                        Set.of(File.separator + Paths.get("git", "renamed.c"))),
                new HistoryEntry("aa35c258", new Date(1218571965000L),
                        "Trond Norbye <trond@sunray-srv.norbye.org>",
                        "    Add lint make target and fix lint warnings\n\n", true,
                        Set.of(File.separator + Paths.get("git", "Makefile"),
                                File.separator + Paths.get("git", "main.c"))),
                new HistoryEntry("84821564", new Date(1218571643000L),
                        "Trond Norbye <trond@sunray-srv.norbye.org>",
                        "    Add the result of a make on Solaris x86\n\n", true,
                        Set.of(File.separator + Paths.get("git", "main.o"),
                                File.separator + Paths.get("git", "testsprog"))),
                new HistoryEntry("bb74b7e8", new Date(1218571573000L),
                        "Trond Norbye <trond@sunray-srv.norbye.org>",
                        "    Added a small test program\n\n", true,
                        Set.of(File.separator + Paths.get("git", "Makefile"),
                                File.separator + Paths.get("git", "header.h"),
                                File.separator + Paths.get("git", "main.c"))));

        List<String> expectedRenamedFiles = List.of(
                File.separator + Paths.get("git", "moved", "renamed2.c"),
                File.separator + Paths.get("git", "moved2", "renamed2.c"),
                File.separator + Paths.get("git", "moved", "renamed.c"));

        History history = gitrepo.getHistory(root);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(entries.size(), history.getHistoryEntries().size());

        History expectedHistory;
        if (renamedHandling) {
            expectedHistory = new History(entries, expectedRenamedFiles);
        } else {
            expectedHistory = new History(entries);
        }
        assertEquals(expectedHistory, history);

        // Retry with start changeset.
        history = gitrepo.getHistory(root, "ce4c98ec");
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(4, history.getHistoryEntries().size());
        if (renamedHandling) {
            expectedHistory = new History(entries.subList(0, 4), expectedRenamedFiles);
            assertEquals(expectedRenamedFiles.size(), history.getRenamedFiles().size());
        } else {
            expectedHistory = new History(entries.subList(0, 4));
            assertEquals(0, history.getRenamedFiles().size());
        }
        assertEquals(expectedHistory, history);
    }

    @Test
    void testSingleHistory() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(false);
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(new File(root.getAbsolutePath(),
                Paths.get("moved2", "renamed2.c").toString()));
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(1, history.getHistoryEntries().size());
        assertEquals("84599b3c", history.getHistoryEntries().get(0).getRevision());
    }

    @Test
    void testRenamedSingleHistory() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(true);
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(new File(root.getAbsolutePath(), "moved2/renamed2.c"));
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(5, history.getHistoryEntries().size());

        assertNotNull(history.getRenamedFiles());
        assertEquals(0, history.getRenamedFiles().size());

        assertEquals("84599b3c", history.getHistoryEntries().get(0).getRevision());
        assertEquals("67dfbe26", history.getHistoryEntries().get(1).getRevision());
        assertEquals("1086eaf5", history.getHistoryEntries().get(2).getRevision());
        assertEquals("b6413947", history.getHistoryEntries().get(3).getRevision());
        assertEquals("ce4c98ec", history.getHistoryEntries().get(4).getRevision());
    }

    @Test
    void testGetHistorySinceTillNullNull() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root, null, null);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(8, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of("84599b3c", "67dfbe26", "1086eaf5", "b6413947", "ce4c98ec", "aa35c258", "84821564",
                "bb74b7e8"), revisions);
    }

    @Test
    void testGetHistorySinceTillNullRev() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root, null, "ce4c98ec");
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(4, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of("ce4c98ec", "aa35c258", "84821564", "bb74b7e8"), revisions);
    }

    @Test
    void testGetHistorySinceTillRevNull() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root, "aa35c258", null);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(5, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of("84599b3c", "67dfbe26", "1086eaf5", "b6413947", "ce4c98ec"), revisions);
    }

    @Test
    void testGetHistorySinceTillRevRev() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root, "ce4c98ec", "1086eaf5");
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(2, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of("1086eaf5", "b6413947"), revisions);
    }

    @Test
    void testBuildTagListEmpty() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        // Clone under source root to avoid problems with prohibited symlinks.
        File localPath = new File(repository.getSourceRoot(), "testBuildTagListEmpty");
        String cloneUrl = root.toURI().toString();
        try (Git gitClone = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(localPath)
                .call()) {

            File cloneRoot = gitClone.getRepository().getWorkTree();

            GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(cloneRoot);
            gitrepo.buildTagList(new File(gitrepo.getDirectoryName()), CommandTimeoutType.INDEXER);
            assertEquals(0, gitrepo.getTagList().size());

            removeRecursive(cloneRoot);
        }
    }

    @Test
    void testBuildTagListMultipleTags() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        // Clone under source root to avoid problems with prohibited symlinks.
        File localPath = new File(repository.getSourceRoot(), "testBuildTagListMultipleTags");
        String cloneUrl = root.toURI().toString();
        try (Git gitClone = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(localPath)
                .call()) {

            File cloneRoot = gitClone.getRepository().getWorkTree();
            GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(cloneRoot);

            // Tag the HEAD first.
            Ref ref = gitClone.tag().setName("one").call();
            assertNotNull(ref);
            gitrepo.buildTagList(new File(gitrepo.getDirectoryName()), CommandTimeoutType.INDEXER);
            Set<TagEntry> tags = gitrepo.getTagList();
            assertEquals(1, tags.size());
            Date date = new Date((long) (1485438707) * 1000);
            TagEntry tagEntry = new GitTagEntry("84599b3cccb3eeb5aa9aec64771678d6526bcecb", date, "one");
            assertEquals(tagEntry, tags.toArray()[0]);

            // Tag again so that single changeset has multiple tags.
            ref = gitClone.tag().setName("two").call();
            assertNotNull(ref);
            gitrepo.buildTagList(new File(gitrepo.getDirectoryName()), CommandTimeoutType.INDEXER);
            tags = gitrepo.getTagList();
            assertEquals(1, tags.size());
            Set<TagEntry> expectedTags = new TreeSet<>();
            date = new Date((long) (1485438707) * 1000);
            tagEntry = new GitTagEntry("84599b3cccb3eeb5aa9aec64771678d6526bcecb", date, "one, two");
            expectedTags.add(tagEntry);
            assertEquals(expectedTags, tags);

            removeRecursive(cloneRoot);
        }
    }

    @Test
    void testBuildTagListNotHead() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        // Clone under source root to avoid problems with prohibited symlinks.
        File localPath = new File(repository.getSourceRoot(), "testBuildTagListNotHead");
        String cloneUrl = root.toURI().toString();
        try (Git gitClone = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(localPath)
                .call()) {

            File cloneRoot = gitClone.getRepository().getWorkTree();
            GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(cloneRoot);

            // Tag specific changeset (not HEAD) and recheck.
            org.eclipse.jgit.lib.Repository repo = gitClone.getRepository();
            RevCommit commit;
            ObjectId objectId = repo.resolve("b6413947a5");
            try (RevWalk walk = new RevWalk(repo)) {
                commit = walk.parseCommit(objectId);
            }
            assertNotNull(commit);
            Ref ref = gitClone.tag().setName("three").setObjectId(commit).call();
            assertNotNull(ref);
            gitrepo.buildTagList(new File(gitrepo.getDirectoryName()), CommandTimeoutType.INDEXER);
            Set<TagEntry> tags = gitrepo.getTagList();
            assertEquals(1, tags.size());
            Date date = new Date((long) (1485263264) * 1000);
            Set<TagEntry> expectedTags = new TreeSet<>();
            TagEntry tagEntry = new GitTagEntry("b6413947a59f481ddc0a05e0d181731233557f6e", date, "three");
            expectedTags.add(tagEntry);
            assertEquals(expectedTags, tags);

            removeRecursive(cloneRoot);
        }
    }

    private void removeRecursive(final File cloneRoot) {
        try {
            IOUtils.removeRecursive(cloneRoot.toPath());
        } catch (IOException e) {
            // ignore
        }
    }

    private String addSubmodule(String submoduleName) throws Exception {
        // Create new Git repository first.
        File newRepoFile = new File(repository.getSourceRoot(), submoduleName);
        Git newRepo = Git.init().setDirectory(newRepoFile).call();
        assertNotNull(newRepo);

        // Add this repository as a submodule to the existing Git repository.
        org.eclipse.jgit.lib.Repository mainRepo = new FileRepositoryBuilder().
                setGitDir(Paths.get(repository.getSourceRoot(), "git", Constants.DOT_GIT).toFile())
                .build();
        String parent = newRepoFile.toPath().toUri().toString();
        try (Git git = new Git(mainRepo)) {
            git.submoduleAdd().
                    setURI(parent).
                    setPath(submoduleName).
                    call();
        }

        return parent;
    }

    @Test
    void testSubmodule() throws Exception {
        String submoduleName = "submodule";
        String submoduleOriginPath = addSubmodule(submoduleName);
        Path submodulePath = Paths.get(repository.getSourceRoot(), "git", submoduleName);

        Repository subRepo = RepositoryFactory.getRepository(submodulePath.toFile());
        assertNotNull(subRepo);
        assertNotNull(subRepo.getParent());
        assertEquals(submoduleOriginPath, subRepo.getParent());

        // Test relative path too. JGit always writes absolute path so overwrite the contents directly.
        File gitFile = Paths.get(submodulePath.toString(), Constants.DOT_GIT).toFile();
        assertTrue(gitFile.isFile());
        try (Writer writer = new FileWriter(gitFile)) {
            writer.write(Constants.GITDIR + ".." + File.separator + Constants.DOT_GIT +
                    File.separator + Constants.MODULES + File.separator + submoduleName);
        }
        subRepo = RepositoryFactory.getRepository(submodulePath.toFile());
        assertNotNull(subRepo);
        assertNotNull(subRepo.getParent());

        removeRecursive(submodulePath.toFile());
    }
}
