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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opensolaris.opengrok.util.Executor;

/**
 *
 * @author austvik
 */
public class GitRepositoryTest {

    GitRepository instance;

    public GitRepositoryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance = new GitRepository();
    }

    @After
    public void tearDown() {
        instance = null;
    }

    /**
     * Test of getHistoryParser method, of class GitRepository.
     */
    @Test
    public void getHistoryParser() {
        Class<? extends HistoryParser> expResult = GitHistoryParser.class;
        Class<? extends HistoryParser> result = instance.getHistoryParser();
        assertEquals(expResult, result);
    }

    /**
     * Test of getDirectoryHistoryParser method, of class GitRepository.
     */
    @Test
    public void getDirectoryHistoryParser() {
        Class<? extends HistoryParser> expResult = GitHistoryParser.class;
        Class<? extends HistoryParser> result = instance.getDirectoryHistoryParser();
        assertEquals(expResult, result);
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

}