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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.FileUtilities;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.GIT;

/**
 * @author austvik
 */
@EnabledForRepository(GIT)
public class GitRepositoryTest {

    private static TestRepository repository = new TestRepository();
    private GitRepository instance;

    @BeforeAll
    public static void setUpClass() throws IOException {
        repository.create(GitRepositoryTest.class.getResourceAsStream("repositories.zip"));
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
    public void testDetermineCurrentVersionWithEmptyRepository() throws Exception {
        File emptyGitDir = new File(repository.getSourceRoot(), "gitEmpty");
        try (Git git = Git.init().setDirectory(emptyGitDir).call()) {
            GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(git.getRepository().getWorkTree());
            assertNotNull(gitrepo);
            String ver = gitrepo.determineCurrentVersion();
            assertNull(ver);
            FileUtilities.removeDirs(emptyGitDir);
        }
    }

    @Test
    public void testDetermineCurrentVersionOfKnownRepository() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        assertNotNull(gitrepo);
        String ver = gitrepo.determineCurrentVersion();
        assertNotNull(ver);
        Date date = new Date((long) (1485438707) * 1000);
        assertEquals(Repository.format(date) + " 84599b3 Kryštof Tulinger renaming directories", ver);
    }

    @Test
    public void testDetermineCurrentVersionAfterChange() throws Exception {
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

            FileUtilities.removeDirs(cloneRoot);
        }
    }

    @Test
    public void testDetermineBranchBasic() throws Exception {
        // First check branch of known repository.
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        String branch = gitrepo.determineBranch();
        assertNotNull(branch);
        assertEquals("master", branch);
    }

    @Test
    public void testDetermineBranchAfterChange() throws Exception {
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

            FileUtilities.removeDirs(cloneRoot);
        }
    }

    @Test
    public void testDetermineParentEmpty() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        String parent = gitrepo.determineParent();
        assertNull(parent);
    }

    @Test
    public void testDetermineParent() throws Exception {
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

            FileUtilities.removeDirs(cloneRoot);
        }
    }

    /**
     * Test of fileHasAnnotation method, of class GitRepository.
     */
    @Test
    public void fileHasAnnotation() {
        boolean result = instance.fileHasAnnotation(null);
        assertTrue(result);
    }

    /**
     * Test of fileHasHistory method, of class GitRepository.
     */
    @Test
    public void fileHasHistory() {
        boolean result = instance.fileHasHistory(null);
        assertTrue(result);
    }

    @Test
    public void testDateFormats() {
        String[][] tests = new String[][] {
                {"abcd", "expected exception"},
                {"2016-01-01 10:00:00", "expected exception"},
                {"2016 Sat, 5 Apr 2008 15:12:51 +0000", "expected exception"},
                {"2017-07-25T13:17:44+02:00", null},
        };

        final GitRepository repository = new GitRepository();

        for (String[] test : tests) {
            try {
                repository.parse(test[0]);
                assertNull(test[1], "Shouldn't be able to parse the date: " + test[0]);
            } catch (ParseException ex) {
                assertNotNull(test[1], "Shouldn't throw a parsing exception for date: " + test[0]);
            }
        }
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
    public void testRenamedFiles() throws Exception {
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
    public void testAnnotationOfRenamedFileWithHandlingOff() throws Exception {
        String[] revisions = {"84599b3c"};
        Set<String> revSet = new HashSet<>();
        Collections.addAll(revSet, revisions);

        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.setHandleRenamedFiles(false);
        File renamedFile = Paths.get(root.getAbsolutePath(), "moved2", "renamed2.c").toFile();
        testAnnotationOfFile(gitrepo, renamedFile, null, revSet);
    }

    @Test
    public void testAnnotationOfRenamedFileWithHandlingOn() throws Exception {
        String[] revisions = {"1086eaf5", "ce4c98ec"};
        Set<String> revSet = new HashSet<>();
        Collections.addAll(revSet, revisions);

        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.setHandleRenamedFiles(true);
        File renamedFile = Paths.get(root.getAbsolutePath(), "moved2", "renamed2.c").toFile();
        testAnnotationOfFile(gitrepo, renamedFile, null, revSet);
    }

    @Test
    public void testAnnotationOfRenamedFilePastWithHandlingOn() throws Exception {
        String[] revisions = {"1086eaf5", "ce4c98ec"};
        Set<String> revSet = new HashSet<>();
        Collections.addAll(revSet, revisions);

        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.setHandleRenamedFiles(true);
        File renamedFile = Paths.get(root.getAbsolutePath(), "moved2", "renamed2.c").toFile();
        testAnnotationOfFile(gitrepo, renamedFile, "1086eaf5", revSet);
    }

    @Test
    public void testInvalidRenamedFiles() {
        String[][] tests = new String[][] {
                {"", "67dfbe26"},
                {"moved/renamed2.c", ""},
                {"", ""},
                {null, "67dfbe26"},
                {"moved/renamed2.c", null}

        };
        assertThrows(IOException.class, () -> {
            File root = new File(repository.getSourceRoot(), "git");
            GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

            for (String[] test : tests) {
                String file = test[0];
                String changeset = test[1];
                gitrepo.findOriginalName(file, changeset);
            }
        });
    }

    /**
     * Test that {@code getHistoryGet()} returns historical contents of renamed
     * file.
     * @see #testRenamedFiles for git repo structure info
     */
    @Test
    public void testGetRenamedFileContent() throws Exception {
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
    public void testGetHistoryForNonExistentRenamed() throws Exception {
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
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);
        byte[] buffer = new byte[4096];

        InputStream input = gitrepo.getHistoryGet(root.getCanonicalPath(),
                fname, cset);
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

    @Test
    public void testHistory() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(false);
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(8, history.getHistoryEntries().size());
        assertEquals(0, history.getRenamedFiles().size());

        History expectedHistory = new History(List.of(
                new HistoryEntry("84599b3c", new Date(1485438707000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                    "    renaming directories\n\n", true,
                        Set.of("/git/moved2/renamed2.c")),
                new HistoryEntry("67dfbe26", new Date(1485263397000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                        "    renaming renamed -> renamed2\n\n", true,
                        Set.of("/git/moved/renamed2.c")),
                new HistoryEntry("1086eaf5", new Date(1485263368000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                        "     adding some lines into renamed.c\n\n", true,
                        Set.of("/git/moved/renamed.c")),
                new HistoryEntry("b6413947", new Date(1485263264000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                        "    moved renamed.c to new location\n\n", true,
                        Set.of("/git/moved/renamed.c")),
                new HistoryEntry("ce4c98ec", new Date(1485263232000L),
                        "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                        "    adding simple file for renamed file testing\n\n", true,
                        Set.of("/git/renamed.c")),
                new HistoryEntry("aa35c258", new Date(1218571965000L),
                        "Trond Norbye <trond@sunray-srv.norbye.org>", null,
                        "    Add lint make target and fix lint warnings\n\n", true,
                        Set.of("/git/Makefile", "/git/main.c")),
                new HistoryEntry("84821564", new Date(1218571643000L),
                        "Trond Norbye <trond@sunray-srv.norbye.org>", null,
                        "    Add the result of a make on Solaris x86\n\n", true,
                        Set.of("/git/main.o", "/git/testsprog")),
                new HistoryEntry("bb74b7e8", new Date(1218571573000L),
                        "Trond Norbye <trond@sunray-srv.norbye.org>", null,
                        "    Added a small test program\n\n", true,
                        Set.of("/git/Makefile", "/git/header.h", "/git/main.c"))));
        assertEquals(expectedHistory, history);
    }

    @Test
    public void testRenamedHistory() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(true);
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(8, history.getHistoryEntries().size());

        assertNotNull(history.getRenamedFiles());
        assertEquals(3, history.getRenamedFiles().size());

        assertTrue(history.isRenamed("moved/renamed2.c"));
        assertTrue(history.isRenamed("moved2/renamed2.c"));
        assertTrue(history.isRenamed("moved/renamed.c"));
        assertFalse(history.isRenamed("non-existent.c"));
        assertFalse(history.isRenamed("renamed.c"));

        assertEquals("84599b3c", history.getHistoryEntries().get(0).getRevision());
        assertEquals("67dfbe26", history.getHistoryEntries().get(1).getRevision());
        assertEquals("1086eaf5", history.getHistoryEntries().get(2).getRevision());
        assertEquals("b6413947", history.getHistoryEntries().get(3).getRevision());
        assertEquals("ce4c98ec", history.getHistoryEntries().get(4).getRevision());
        assertEquals("aa35c258", history.getHistoryEntries().get(5).getRevision());
        assertEquals("84821564", history.getHistoryEntries().get(6).getRevision());
        assertEquals("bb74b7e8", history.getHistoryEntries().get(7).getRevision());
    }

    @Test
    public void testRenamedSingleHistory() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(true);
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(new File(root.getAbsolutePath(), "moved2/renamed2.c"));
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        assertEquals(5, history.getHistoryEntries().size());

        assertNotNull(history.getRenamedFiles());
        assertEquals(3, history.getRenamedFiles().size());

        assertTrue(history.isRenamed("moved/renamed2.c"));
        assertTrue(history.isRenamed("moved2/renamed2.c"));
        assertTrue(history.isRenamed("moved/renamed.c"));
        assertFalse(history.isRenamed("non-existent.c"));
        assertFalse(history.isRenamed("renamed.c"));

        assertEquals("84599b3c", history.getHistoryEntries().get(0).getRevision());
        assertEquals("67dfbe26", history.getHistoryEntries().get(1).getRevision());
        assertEquals("1086eaf5", history.getHistoryEntries().get(2).getRevision());
        assertEquals("b6413947", history.getHistoryEntries().get(3).getRevision());
        assertEquals("ce4c98ec", history.getHistoryEntries().get(4).getRevision());
    }

    @Test
    public void testBuildTagListEmpty() throws Exception {
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

            FileUtilities.removeDirs(cloneRoot);
        }
    }

    @Test
    public void testBuildTagListMultipleTags() throws Exception {
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

            FileUtilities.removeDirs(cloneRoot);
        }
    }

    @Test
    public void testBuildTagListNotHead() throws Exception {
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

            FileUtilities.removeDirs(cloneRoot);
        }
    }
}
