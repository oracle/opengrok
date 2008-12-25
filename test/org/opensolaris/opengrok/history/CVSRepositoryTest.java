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
import java.io.Reader;
import java.io.StringReader;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author austvik
 */
public class CVSRepositoryTest {

    CVSRepository instance;

    public CVSRepositoryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance = new CVSRepository();
    }

    @After
    public void tearDown() {
        instance = null;
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
     * Test of getHistoryParser method, of class CVSRepository.
     */
    @Test
    public void getHistoryParser() {
        Class<? extends HistoryParser> expResult = CVSHistoryParser.class;
        Class<? extends HistoryParser> result = instance.getHistoryParser();
        assertEquals(expResult, result);
    }

    /**
     * Test of getDirectoryHistoryParser method, of class CVSRepository.
     */
    @Test
    public void getDirectoryHistoryParser() {
        Class<? extends HistoryParser> expResult = CVSHistoryParser.class;
        Class<? extends HistoryParser> result = instance.getDirectoryHistoryParser();
        assertEquals(expResult, result);
    }

    /**
     * Test of parseAnnotation method, of class CVSRepository.
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
