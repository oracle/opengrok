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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.CVS;

/**
 * Tests for {@link CVSRepository} functionality.
 * @author austvik
 */
@EnabledForRepository(CVS)
public class CVSRepositoryTest {

    CVSRepository instance;

    private TestRepository repository;

    /**
     * Set up a test repository. Should be called by the tests that need it. The
     * test repository will be destroyed automatically when the test finishes.
     */
    private void setUpTestRepository() throws IOException, URISyntaxException {
        repository = new TestRepository();
        repository.create(getClass().getResource("/repositories"));

        // Checkout cvsrepo anew in order to get the CVS/Root files point to
        // the temporary directory rather than the OpenGrok workspace directory
        // it was created from. This is necessary since 'cvs update' changes
        // the CVS parent directory after branch has been created.
        File root = new File(repository.getSourceRoot(), "cvs_test");
        File cvsrepodir = new File(root, "cvsrepo");
        IOUtils.removeRecursive(cvsrepodir.toPath());
        File cvsroot = new File(root, "cvsroot");
        runCvsCommand(root, "-d", cvsroot.getAbsolutePath(), "checkout", "cvsrepo");
    }

    @AfterEach
    public void tearDown() {
        instance = null;

        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    @BeforeEach
    public void setUp() {
        instance = new CVSRepository();
    }

    /**
     * Run the 'cvs' command with some arguments.
     *
     * @param reposRoot directory of the repository root
     * @param args arguments to use for the command
     */
    public static void runCvsCommand(File reposRoot, String... args) {
        List<String> cmdargs = new ArrayList<>();
        CVSRepository repo = new CVSRepository();
        cmdargs.add(repo.getRepoCommand());
        Collections.addAll(cmdargs, args);
        Executor exec = new Executor(cmdargs, reposRoot);
        int exitCode = exec.exec();
        assertEquals(0, exitCode, "cvs command '" + cmdargs.toString() + "'failed."
                + "\nexit code: " + exitCode
                + "\nstdout:\n" + exec.getOutputString()
                + "\nstderr:\n" + exec.getErrorString());
    }

    /**
     * Get the CVS repository, test that getBranch() returns null if there is no branch.
     */
    @Test
    void testGetBranchNoBranch() throws Exception {
        setUpTestRepository();
        File root = Path.of(repository.getSourceRoot(), "cvs_test", "cvsrepo").toFile();
        CVSRepository cvsrepo = (CVSRepository) RepositoryFactory.getRepository(root);
        assertNull(cvsrepo.getBranch());
    }

    /**
     * Get the CVS repository, create new branch, change a file and verify that
     * getBranch() returns the branch and check newly added commits annotate
     * with branch revision numbers.
     * Last, check that history entries of the file follow through before the
     * branch was created.
     */
    @Test
    void testNewBranch() throws Exception {
        setUpTestRepository();
        File root = Path.of(repository.getSourceRoot(), "cvs_test", "cvsrepo").toFile();

        // Create new branch and switch to it.
        runCvsCommand(root, "tag", "-b", "mybranch");
        // Note that the 'update' command will change the entries in 'cvsroot' directory.
        runCvsCommand(root, "update", "-r", "mybranch");

        // Now the repository object can be instantiated and determineBranch() will be called.
        CVSRepository cvsrepo = (CVSRepository) RepositoryFactory.getRepository(root);

        assertEquals("mybranch", cvsrepo.getBranch());

        // Change the content and commit.
        File mainC = new File(root, "main.c");
        try (FileChannel outChan = new FileOutputStream(mainC, true).getChannel()) {
            outChan.truncate(0);
        }
        try (FileWriter fw = new FileWriter(mainC)) {
            fw.write("#include <foo.h>\n");
        }
        runCvsCommand(root, "commit", "-m", "change on a branch", "main.c");

        // Check that annotation for the changed line has branch revision.
        Annotation annotation = cvsrepo.annotate(mainC, null);
        assertEquals("1.2.2.1", annotation.getRevision(1));

        History mainCHistory = cvsrepo.getHistory(mainC);
        assertEquals(3, mainCHistory.getHistoryEntries().size());
        assertEquals("1.2.2.1", mainCHistory.getHistoryEntries().get(0).getRevision());
        assertEquals("1.2", mainCHistory.getHistoryEntries().get(1).getRevision());
        assertEquals("1.1", mainCHistory.getHistoryEntries().get(2).getRevision());
    }

    @Test
    void testGetHistoryGet() throws Exception {
        setUpTestRepository();
        File root = Path.of(repository.getSourceRoot(), "cvs_test", "cvsrepo").toFile();
        CVSRepository cvsRepo = (CVSRepository) RepositoryFactory.getRepository(root);

        Path repoRoot = Path.of(repository.getSourceRoot(), "cvs_test", "cvsrepo");
        assertTrue(repoRoot.toFile().exists());
        String repoFile = "main.c";
        String revision = "1.2";

        File tmpFile = File.createTempFile("cvsGetHistoryGetTest", "");
        assertTrue(tmpFile.exists());
        try (FileOutputStream out = new FileOutputStream(tmpFile)) {
            assertTrue(cvsRepo.getHistoryGet(out, repoRoot.toString(), repoFile, revision));
        }

        String revisionContents1 = new String(Files.readAllBytes(tmpFile.toPath()));
        tmpFile.delete();
        assertTrue(revisionContents1.length() > 0);

        String revisionContents2 = new String(cvsRepo.getHistoryGet(repoRoot.toString(), repoFile, revision).
                readAllBytes());
        assertEquals(revisionContents1, revisionContents2);
    }

    /**
     * Assert that revision strings in history entries are sorted semantically.
     * This is necessary for displaying revisions on a branch in correct order.
     */
    @Test
    void testRevisionSorting() {
        HistoryEntry[] entries = {
                                    new HistoryEntry("1.1"),
                                    new HistoryEntry("1.12.200.2.2.3.50.2"),
                                    new HistoryEntry("1.9"),
                                    new HistoryEntry("1.12.200.2.2.3"),
                                    new HistoryEntry("1.2"),
                                    new HistoryEntry("1.12.200.1"),
                                    new HistoryEntry("1.12.200.2.2.2"),
                                 };
        History history = new History(Arrays.stream(entries).collect(Collectors.toList()));
        CVSHistoryParser.sortHistoryEntries(history);
        List<String> revisionsActual = history.getHistoryEntries().stream().
                map(HistoryEntry::getRevision).collect(Collectors.toList());
        List<String> revisionsExpected = List.of("1.12.200.2.2.3.50.2",
                "1.12.200.2.2.3", "1.12.200.2.2.2", "1.12.200.1", "1.9", "1.2", "1.1");
        assertEquals(revisionsExpected, revisionsActual);
    }

    /**
     * Test of fileHasAnnotation method, of class CVSRepository.
     */
    @Test
    void testFileHasAnnotation() {
        boolean result = instance.fileHasAnnotation(null);
        assertTrue(result);
    }

    /**
     * Test of fileHasHistory method, of class CVSRepository.
     */
    @Test
    void testFileHasHistory() {
        boolean result = instance.fileHasHistory(null);
        assertTrue(result);
    }

    /**
     * Test of parseAnnotation method, of class CVSRepository.
     */
    @Test
    void testParseAnnotation() throws Exception {
        String revId1 = "1.1";
        String revId2 = "1.2.3";
        String revId3 = "1.0";
        String author1 = "author1";
        String author2 = "author_long2";
        String author3 = "author3";
        String output = "just jibberish in output\n\n" + revId1 + "     (" + author1 + " 01-Mar-07) \n" +
                revId2 + "    (" + author2 + " 02-Mar-08)   if (some code)\n" +
                revId3 + "       (" + author3 + " 30-Apr-07)           call_function(i);\n";

        String fileName = "something.ext";

        CVSAnnotationParser parser = new CVSAnnotationParser(fileName);
        parser.processStream(new ByteArrayInputStream(output.getBytes()));
        Annotation result = parser.getAnnotation();

        assertNotNull(result);
        assertEquals(3, result.size());
        for (int i = 1; i <= 3; i++) {
            assertTrue(result.isEnabled(i));
        }
        assertEquals(revId1, result.getRevision(1));
        assertEquals(revId2, result.getRevision(2));
        assertEquals(revId3, result.getRevision(3));
        assertEquals(author1, result.getAuthor(1));
        assertEquals(author2, result.getAuthor(2));
        assertEquals(author3, result.getAuthor(3));
        assertEquals(author2.length(), result.getWidestAuthor());
        assertEquals(revId2.length(), result.getWidestRevision());
        assertEquals(fileName, result.getFilename());
    }
}
