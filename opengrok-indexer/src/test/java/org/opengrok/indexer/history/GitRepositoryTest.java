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
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.indexer.history;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author austvik
 */
@ConditionalRun(RepositoryInstalled.GitInstalled.class)
public class GitRepositoryTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    private static TestRepository repository = new TestRepository();
    private GitRepository instance;

    @BeforeClass
    public static void setUpClass() throws IOException {
        repository.create(GitRepositoryTest.class.getResourceAsStream("repositories.zip"));
    }

    @AfterClass
    public static void tearDownClass() {
        repository.destroy();
        repository = null;
    }

    @Before
    public void setUp() {
        instance = new GitRepository();
    }

    @After
    public void tearDown() {
        instance = null;
    }

    @Test
    public void testDetermineCurrentVersion() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);
        String ver = gitrepo.determineCurrentVersion();
        Assert.assertNotNull(ver);
    }

    /**
     * Test of parseAnnotation method, of class GitRepository.
     */
    @Test
    public void parseAnnotation() throws Exception {
        String revId1 = "cd283405560689372626a69d5331c467bce71656";
        String revId2 = "30ae764b12039348766291100308556675ca11ab";
        String revId3 = "2394823984cde2390345435a9237bd7c25932342";
        String author1 = "Author Name";
        String author2 = "Author With Long Name";
        String author3 = "Author Named Jr.";
        String output = revId1 + " file1.ext   (" + author1 + "     2005-06-06 16:38:26 -0400 272) \n" +
                revId2 + " file2.h (" + author2 + "     2007-09-10 23:02:45 -0400 273)   if (some code)\n" +
                revId3 + " file2.c  (" + author3 + "      2006-09-20 21:47:42 -0700 274)           call_function(i);\n";
        String fileName = "something.ext";

        GitAnnotationParser parser = new GitAnnotationParser(fileName);
        parser.processStream(new ByteArrayInputStream(output.getBytes()));
        Annotation result = parser.getAnnotation();

        assertNotNull(result);
        assertEquals(3, result.size());
        for (int i = 1; i <= 3; i++) {
            assertTrue("isEnabled()", result.isEnabled(i));
        }
        assertEquals(revId1, result.getRevision(1));
        assertEquals(revId2, result.getRevision(2));
        assertEquals(revId3, result.getRevision(3));
        assertEquals(author1, result.getAuthor(1));
        assertEquals(author2, result.getAuthor(2));
        assertEquals(author3, result.getAuthor(3));
        assertEquals(author2.length(), result.getWidestAuthor());
        assertEquals(revId1.length(), result.getWidestRevision());
        assertEquals(fileName, result.getFilename());
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
                if (test[1] != null) {
                    Assert.fail("Shouldn't be able to parse the date: " + test[0]);
                }
            } catch (ParseException ex) {
                if (test[1] == null) {
                    // no exception
                    Assert.fail("Shouldn't throw a parsing exception for date: " + test[0]);
                }
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
            try {
                String originalName = gitrepo.findOriginalName(file, changeset);
                Assert.assertEquals(expectedName, originalName);
            } catch (IOException ex) {
                Assert.fail(String.format("Looking for original name of %s in %s shouldn't fail", file, changeset));
            }
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

    @Test(expected = IOException.class)
    public void testInvalidRenamedFiles() throws Exception {
        String[][] tests = new String[][] {
                {"", "67dfbe26"},
                {"moved/renamed2.c", ""},
                {"", ""},
                {null, "67dfbe26"},
                {"moved/renamed2.c", null}

        };
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);

        for (String[] test : tests) {
            String file = test[0];
            String changeset = test[1];
            gitrepo.findOriginalName(file, changeset);
        }
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
            Assert.assertNull(
                    String.format("Expecting the revision %s for file %s does not exist",
                            cset,
                            fname
                    ),
                    input
            );
        } else {
            Assert.assertNotNull(
                    String.format("Expecting the revision %s for file %s does exist",
                            cset,
                            fname
                    ),
                    input
            );
            int len = input.read(buffer);
            Assert.assertNotEquals(
                    String.format("Expecting the revision %s for file %s does have some content",
                            cset,
                            fname
                    ),
                    -1,
                    len
            );
            String str = new String(buffer, 0, len);
            Assert.assertEquals(
                    String.format("Expecting the revision %s for file %s does match the expected content",
                            cset,
                            fname
                    ),
                    content,
                    str
            );
        }
    }

    @Test
    public void testRenamedHistory() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(true);
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(root);
        Assert.assertNotNull(history);
        Assert.assertNotNull(history.getHistoryEntries());
        Assert.assertEquals(8, history.getHistoryEntries().size());

        Assert.assertNotNull(history.getRenamedFiles());
        Assert.assertEquals(3, history.getRenamedFiles().size());

        Assert.assertTrue(history.isRenamed("moved/renamed2.c"));
        Assert.assertTrue(history.isRenamed("moved2/renamed2.c"));
        Assert.assertTrue(history.isRenamed("moved/renamed.c"));
        Assert.assertFalse(history.isRenamed("non-existent.c"));
        Assert.assertFalse(history.isRenamed("renamed.c"));

        Assert.assertEquals("84599b3c", history.getHistoryEntries().get(0).getRevision());
        Assert.assertEquals("67dfbe26", history.getHistoryEntries().get(1).getRevision());
        Assert.assertEquals("1086eaf5", history.getHistoryEntries().get(2).getRevision());
        Assert.assertEquals("b6413947", history.getHistoryEntries().get(3).getRevision());
        Assert.assertEquals("ce4c98ec", history.getHistoryEntries().get(4).getRevision());
        Assert.assertEquals("aa35c258", history.getHistoryEntries().get(5).getRevision());
        Assert.assertEquals("84821564", history.getHistoryEntries().get(6).getRevision());
        Assert.assertEquals("bb74b7e8", history.getHistoryEntries().get(7).getRevision());
    }

    @Test
    public void testRenamedSingleHistory() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(true);
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);

        History history = gitrepo.getHistory(new File(root.getAbsolutePath(), "moved2/renamed2.c"));
        Assert.assertNotNull(history);
        Assert.assertNotNull(history.getHistoryEntries());
        Assert.assertEquals(5, history.getHistoryEntries().size());

        Assert.assertNotNull(history.getRenamedFiles());
        Assert.assertEquals(3, history.getRenamedFiles().size());

        Assert.assertTrue(history.isRenamed("moved/renamed2.c"));
        Assert.assertTrue(history.isRenamed("moved2/renamed2.c"));
        Assert.assertTrue(history.isRenamed("moved/renamed.c"));
        Assert.assertFalse(history.isRenamed("non-existent.c"));
        Assert.assertFalse(history.isRenamed("renamed.c"));

        Assert.assertEquals("84599b3c", history.getHistoryEntries().get(0).getRevision());
        Assert.assertEquals("67dfbe26", history.getHistoryEntries().get(1).getRevision());
        Assert.assertEquals("1086eaf5", history.getHistoryEntries().get(2).getRevision());
        Assert.assertEquals("b6413947", history.getHistoryEntries().get(3).getRevision());
        Assert.assertEquals("ce4c98ec", history.getHistoryEntries().get(4).getRevision());
    }

    @Test
    public void testBuildTagList() throws Exception {
        File root = new File(repository.getSourceRoot(), "git");
        GitRepository gitrepo
                = (GitRepository) RepositoryFactory.getRepository(root);
        gitrepo.buildTagList(new File(gitrepo.getDirectoryName()), CommandTimeoutType.INDEXER);
        assertEquals(0, gitrepo.getTagList().size());

        // TODO: add some tags (using JGit), rebuild tag list
    }
}
