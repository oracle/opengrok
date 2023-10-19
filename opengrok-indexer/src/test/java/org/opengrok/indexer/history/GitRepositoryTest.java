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
 * Portions Copyright (c) 2023, Ric Harris <harrisric@users.noreply.github.com>.
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
class GitRepositoryTest {

    private static final String HASH_84821564 = "8482156421620efbb44a7b6f0eb19d1f191163c7";
    private static final String HASH_AA35C258 = "aa35c25882b9a60a97758e0ceb276a3f8cb4ae3a";
    private static final String HASH_BB74B7E8 = "bb74b7e849170c31dc1b1b5801c83bf0094a3b10";
    private static final String HASH_CE4C98EC = "ce4c98ec1d22473d4aa799c046c2a90ae05832f1";
    private static final String HASH_B6413947 = "b6413947a59f481ddc0a05e0d181731233557f6e";
    private static final String HASH_1086EAF5 = "1086eaf5bca6d5a056097aa76017a8ab0eade20f";
    private static final String HASH_67DFBE26 = "67dfbe2648c94a8825671b0f2c132828d0d43079";
    private static final String HASH_84599B3C = "84599b3cccb3eeb5aa9aec64771678d6526bcecb";
    private static final String ABRV_HASH_84821564 = HASH_84821564.substring(0, 8);
    private static final String ABRV_HASH_AA35C258 = HASH_AA35C258.substring(0, 8);
    private static final String ABRV_HASH_BB74B7E8 = HASH_BB74B7E8.substring(0, 8);
    private static final String ABRV_HASH_CE4C98EC = HASH_CE4C98EC.substring(0, 8);
    private static final String ABRV_HASH_B6413947 = HASH_B6413947.substring(0, 8);
    private static final String ABRV_HASH_1086EAF5 = HASH_1086EAF5.substring(0, 8);
    private static final String ABRV_HASH_67DFBE26 = HASH_67DFBE26.substring(0, 8);
    private static final String ABRV_HASH_84599B3C = HASH_84599B3C.substring(0, 8);
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
    void setUp() {
        instance = new GitRepository();
    }

    @AfterEach
    void tearDown() {
        instance = null;
    }

    private void checkCurrentVersion(File root, int timestamp, String commitId, String shortComment)
            throws Exception {
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        assertNotNull(gitrepo);
        String ver = gitrepo.determineCurrentVersion();
        assertNotNull(ver);
        Date date = new Date((long) timestamp * 1000);
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
        Date date = new Date((long) 1485438707 * 1000);
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
                {Paths.get("moved2", "renamed2.c").toString(), HASH_84599B3C, Paths.get("moved2", "renamed2.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), HASH_67DFBE26, Paths.get("moved", "renamed2.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), HASH_67DFBE26, Paths.get("moved", "renamed2.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), HASH_1086EAF5, Paths.get("moved", "renamed.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), HASH_B6413947, Paths.get("moved", "renamed.c").toString()},
                {Paths.get("moved2", "renamed2.c").toString(), HASH_CE4C98EC, "renamed.c"},
                {Paths.get("moved2", "renamed2.c").toString(), HASH_BB74B7E8, "renamed.c"}
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
        String[] revisions = {HASH_84599B3C};
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
        String[] revisions = {HASH_1086EAF5, HASH_CE4C98EC};
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
        String[] revisions = {HASH_1086EAF5, HASH_CE4C98EC};
        Set<String> revSet = new HashSet<>();
        Collections.addAll(revSet, revisions);

        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.setHandleRenamedFiles(true);
        File renamedFile = Paths.get(root.getAbsolutePath(), "moved2", "renamed2.c").toFile();
        testAnnotationOfFile(gitrepo, renamedFile, HASH_1086EAF5, revSet);
    }

    @Test
    void testInvalidRenamedFiles() throws Exception {
        String[][] tests = new String[][] {
                {"", HASH_67DFBE26},
                {"moved/renamed2.c", ""},
                {"", ""},
                {null, HASH_67DFBE26},
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
                new String[] {Paths.get("moved2", "renamed2.c").toString(), HASH_84599B3C, new_content},
                new String[] {Paths.get("moved2", "renamed2.c").toString(), HASH_67DFBE26, new_content},
                new String[] {Paths.get("moved2", "renamed2.c").toString(), HASH_1086EAF5, new_content},

                new String[] {Paths.get("moved", "renamed2.c").toString(), HASH_67DFBE26, new_content},
                new String[] {Paths.get("moved", "renamed2.c").toString(), HASH_1086EAF5, new_content},

                new String[] {Paths.get("moved", "renamed.c").toString(), HASH_1086EAF5, new_content},

                // old content (before revision b6413947a59f481ddc0a05e0d181731233557f6e inclusively)
                new String[] {Paths.get("moved2", "renamed2.c").toString(), HASH_B6413947, old_content},
                new String[] {Paths.get("moved2", "renamed2.c").toString(), HASH_CE4C98EC, old_content},
                new String[] {Paths.get("moved", "renamed2.c").toString(), HASH_B6413947, old_content},
                new String[] {Paths.get("moved", "renamed2.c").toString(), HASH_CE4C98EC, old_content},
                new String[] {Paths.get("moved", "renamed.c").toString(), HASH_B6413947, old_content},
                new String[] {Paths.get("moved", "renamed.c").toString(), HASH_CE4C98EC, old_content},
                new String[] {Paths.get("renamed.c").toString(), HASH_CE4C98EC, old_content}
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
                new String[] {Paths.get("moved", "renamed2.c").toString(), HASH_84599B3C},

                new String[] {Paths.get("moved", "renamed.c").toString(), HASH_84599B3C},
                new String[] {Paths.get("moved", "renamed.c").toString(), HASH_67DFBE26},

                new String[] {Paths.get("renamed.c").toString(), HASH_84599B3C},
                new String[] {Paths.get("renamed.c").toString(), HASH_67DFBE26},
                new String[] {Paths.get("renamed.c").toString(), HASH_1086EAF5},
                new String[] {Paths.get("renamed.c").toString(), HASH_B6413947}
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
                new HistoryEntry(HASH_84599B3C, ABRV_HASH_84599B3C, new Date(1485438707000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "    renaming directories\n\n", true,
                        Set.of(File.separator + Paths.get("git", "moved2", "renamed2.c"))),
                new HistoryEntry(HASH_67DFBE26, ABRV_HASH_67DFBE26, new Date(1485263397000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "    renaming renamed -> renamed2\n\n", true,
                        Set.of(File.separator + Paths.get("git", "moved", "renamed2.c"))),
                new HistoryEntry(HASH_1086EAF5, ABRV_HASH_1086EAF5, new Date(1485263368000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "     adding some lines into renamed.c\n\n", true,
                        Set.of(File.separator + Paths.get("git", "moved", "renamed.c"))),
                new HistoryEntry(HASH_B6413947, ABRV_HASH_B6413947, new Date(1485263264000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "    moved renamed.c to new location\n\n", true,
                        Set.of(File.separator + Paths.get("git", "moved", "renamed.c"))),
                new HistoryEntry(HASH_CE4C98EC, ABRV_HASH_CE4C98EC, new Date(1485263232000L),  // start in the sub-test below
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>",
                        "    adding simple file for renamed file testing\n\n", true,
                        Set.of(File.separator + Paths.get("git", "renamed.c"))),
                new HistoryEntry(HASH_AA35C258, ABRV_HASH_AA35C258, new Date(1218571965000L),
                        "Trond Norbye <trond@sunray-srv.norbye.org>",
                        "    Add lint make target and fix lint warnings\n\n", true,
                        Set.of(File.separator + Paths.get("git", "Makefile"),
                                File.separator + Paths.get("git", "main.c"))),
                new HistoryEntry(HASH_84821564, ABRV_HASH_84821564, new Date(1218571643000L),
                        "Trond Norbye <trond@sunray-srv.norbye.org>",
                        "    Add the result of a make on Solaris x86\n\n", true,
                        Set.of(File.separator + Paths.get("git", "main.o"),
                                File.separator + Paths.get("git", "testsprog"))),
                new HistoryEntry(HASH_BB74B7E8, ABRV_HASH_BB74B7E8, new Date(1218571573000L),
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
        history = gitrepo.getHistory(root, HASH_CE4C98EC);
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
        assertEquals(HASH_84599B3C, history.getHistoryEntries().get(0).getRevision());
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

        assertEquals(HASH_84599B3C, history.getHistoryEntries().get(0).getRevision());
        assertEquals(HASH_67DFBE26, history.getHistoryEntries().get(1).getRevision());
        assertEquals(HASH_1086EAF5, history.getHistoryEntries().get(2).getRevision());
        assertEquals(HASH_B6413947, history.getHistoryEntries().get(3).getRevision());
        assertEquals(HASH_CE4C98EC, history.getHistoryEntries().get(4).getRevision());
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
        assertEquals(List.of(HASH_84599B3C, HASH_67DFBE26, HASH_1086EAF5, HASH_B6413947, HASH_CE4C98EC, HASH_AA35C258, HASH_84821564,
                HASH_BB74B7E8), revisions);
    }

    @Test
    void testGetHistorySinceTillNullRev() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root, null, HASH_CE4C98EC);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(4, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(HASH_CE4C98EC, HASH_AA35C258, HASH_84821564, HASH_BB74B7E8), revisions);
    }

    @Test
    void testGetHistorySinceTillRevNull() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root, HASH_AA35C258, null);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(5, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(HASH_84599B3C, HASH_67DFBE26, HASH_1086EAF5, HASH_B6413947, HASH_CE4C98EC), revisions);
    }

    @Test
    void testGetHistorySinceTillRevRev() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root, HASH_CE4C98EC, HASH_1086EAF5);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(2, history.getHistoryEntries().size());
        List<String> revisions = history.getHistoryEntries().stream().map(HistoryEntry::getRevision).
                collect(Collectors.toList());
        assertEquals(List.of(HASH_1086EAF5, HASH_B6413947), revisions);
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
            Date date = new Date((long) 1485438707 * 1000);
            TagEntry tagEntry = new GitTagEntry(HASH_84599B3C, date, "one");
            assertEquals(tagEntry, tags.toArray()[0]);

            // Tag again so that single changeset has multiple tags.
            ref = gitClone.tag().setName("two").call();
            assertNotNull(ref);
            gitrepo.buildTagList(new File(gitrepo.getDirectoryName()), CommandTimeoutType.INDEXER);
            tags = gitrepo.getTagList();
            assertEquals(1, tags.size());
            Set<TagEntry> expectedTags = new TreeSet<>();
            date = new Date((long) 1485438707 * 1000);
            tagEntry = new GitTagEntry(HASH_84599B3C, date, "one, two");
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
            ObjectId objectId = repo.resolve(HASH_B6413947);
            try (RevWalk walk = new RevWalk(repo)) {
                commit = walk.parseCommit(objectId);
            }
            assertNotNull(commit);
            Ref ref = gitClone.tag().setName("three").setObjectId(commit).call();
            assertNotNull(ref);
            gitrepo.buildTagList(new File(gitrepo.getDirectoryName()), CommandTimeoutType.INDEXER);
            Set<TagEntry> tags = gitrepo.getTagList();
            assertEquals(1, tags.size());
            Date date = new Date((long) 1485263264 * 1000);
            Set<TagEntry> expectedTags = new TreeSet<>();
            TagEntry tagEntry = new GitTagEntry(HASH_B6413947, date, "three");
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
