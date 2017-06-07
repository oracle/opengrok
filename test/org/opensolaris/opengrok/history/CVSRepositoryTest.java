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
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opensolaris.opengrok.condition.ConditionalRun;
import org.opensolaris.opengrok.condition.ConditionalRunRule;
import org.opensolaris.opengrok.condition.RepositoryInstalled;

import static org.junit.Assert.*;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.TestRepository;

/**
 *
 * @author austvik
 */
@ConditionalRun(condition = RepositoryInstalled.CvsInstalled.class)
public class CVSRepositoryTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    CVSRepository instance;

    public CVSRepositoryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    private TestRepository repository;

    /**
     * Set up a test repository. Should be called by the tests that need it. The
     * test repository will be destroyed automatically when the test finishes.
     */
    private void setUpTestRepository() throws IOException {
        repository = new TestRepository();
        repository.create(getClass().getResourceAsStream("repositories.zip"));

        // Fix the CVS/Root so that it points to the temporary directory
        // rather than the workspace directory.
        // Normally this would be bad idea since every subdirectory includes
        // CVS/Root file however in this case there is just one such file
        // as the repository is flat in terms of directory structure.
        // This is done so that 'cvs update' does not change the "upstream"
        // cvsroot directory entries.
        // The alternative would be to checkout cvsrepo from cvsroot.
        // XXX no proper path separators + newline char below
        File root = new File(repository.getSourceRoot(), "cvs_test/cvsrepo/CVS/Root");
        if (root.isFile()) {
            FileChannel outChan = new FileOutputStream(root, true).getChannel();
            outChan.truncate(0);
            outChan.close();
        }
        FileWriter fw = new FileWriter(root);
        fw.write(repository.getSourceRoot() + "/cvs_test/cvsroot\n");
        fw.close();
    }

    @After
    public void tearDown() {
        instance = null;

        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    @Before
    public void setUp() {
        instance = new CVSRepository();
    }

    /**
     * Run the 'cvs' command.
     *
     * @param reposRoot directory of the repository root
     * @param command command to run
     * @param arg argument to use for the command
     */
    private static void runCvsCommand(File reposRoot, String command, String ... args) {
        List<String> cmdargs = new ArrayList<>();
        cmdargs.add(CVSRepository.CMD_FALLBACK);
        cmdargs.add(command);
        for (String arg: args) {
            cmdargs.add(arg);
        }
        Executor exec = new Executor(cmdargs, reposRoot);
        int exitCode = exec.exec();
        if (exitCode != 0) {
            fail("cvs " + command + " failed."
                    + "\nexit code: " + exitCode
                    + "\nstdout:\n" + exec.getOutputString()
                    + "\nstderr:\n" + exec.getErrorString());
        }
    }

    /**
     * Get the CVS repository, test that getBranch() returns null if there is
     * no branch.
     * @throws Exception
     */
    @Test
    public void testGetBranchNoBranch() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "cvs_test/cvsrepo");
        CVSRepository cvsrepo
                = (CVSRepository) RepositoryFactory.getRepository(root);
        assertEquals(null, cvsrepo.getBranch());
    }

    /**
     * Get the CVS repository, create new branch and verify getBranch() returns it.
     * @throws Exception
     */
    @Test
    public void testGetBranchNewBranch() throws Exception {
        setUpTestRepository();
        File root = new File(repository.getSourceRoot(), "cvs_test/cvsrepo");

        // Create new branch and switch to it.
        runCvsCommand(root, "tag", "-b", "mybranch");
        // Note that the 'update' command will change the entries in 'cvsroot' directory.
        runCvsCommand(root, "update", "-r", "mybranch");

        // Now the repository object can be instantiated so that determineBranch()
        // will be called.
        CVSRepository cvsrepo
            = (CVSRepository) RepositoryFactory.getRepository(root);

        assertEquals("mybranch", cvsrepo.getBranch());
    }

    /**
     * Test of fileHasAnnotation method, of class CVSRepository.
     */
    @Test
    public void testFileHasAnnotation() {
        boolean result = instance.fileHasAnnotation(null);
        assertTrue(result);
    }

    /**
     * Test of fileHasHistory method, of class CVSRepository.
     */
    @Test
    public void testFileHasHistory() {
        boolean result = instance.fileHasHistory(null);
        assertTrue(result);
    }

    /**
     * Test of parseAnnotation method, of class CVSRepository.
     * @throws java.lang.Exception
     */
    @Test
    public void testParseAnnotation() throws Exception {
        String revId1 = "1.1";
        String revId2 = "1.2.3";
        String revId3 = "1.0";
        String author1 = "author1";
        String author2 = "author_long2";
        String author3 = "author3";
        String output = "just jibberish in output\n\n" + revId1 + "     (" + author1 + " 01-Mar-07) \n" +
                revId2 + "    (" + author2 + " 02-Mar-08)   if (some code)\n" +
                revId3 + "       (" + author3 + " 30-Apr-07)           call_function(i);\n";
        Reader input = new StringReader(output);
        String fileName = "something.ext";
        Annotation result = instance.parseAnnotation(input, fileName);
        assertNotNull(result);
        assertEquals(3, result.size());
        for (int i = 1; i <= 3; i++) {
            assertEquals(true, result.isEnabled(i));
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
