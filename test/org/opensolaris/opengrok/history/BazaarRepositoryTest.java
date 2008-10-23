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
public class BazaarRepositoryTest {

    BazaarRepository instance;

    public BazaarRepositoryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance = new BazaarRepository();
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
        Class<? extends HistoryParser> expResult = BazaarHistoryParser.class;
        Class<? extends HistoryParser> result = instance.getHistoryParser();
        assertEquals(expResult, result);
    }

    /**
     * Test of getDirectoryHistoryParser method, of class GitRepository.
     */
    @Test
    public void getDirectoryHistoryParser() {
        Class<? extends HistoryParser> expResult = BazaarHistoryParser.class;
        Class<? extends HistoryParser> result = instance.getDirectoryHistoryParser();
        assertEquals(expResult, result);
    }

    /**
     * Test of parseAnnotation method, of class GitRepository.
     */
    @Test
    public void parseAnnotation() throws Exception {
        String revId1 = "1234.876.5";
        String revId2 = "1.234";
        String revId3 = "2";
        String author1 = "username@example.com";
        String author2 = "username2@example.com";
        String author3 = "username3@example.com";
        String output = revId1 + "  " + author1 + " 20050912 | some source code here\n" +
                revId2 + "  " + author2 + " 20050912 | and here.\n" +
                revId3 + "           " + author3 + "          20030731 | \n";
       
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