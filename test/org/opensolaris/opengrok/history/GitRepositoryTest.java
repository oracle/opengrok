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

import java.io.Reader;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opensolaris.opengrok.condition.ConditionalRun;
import org.opensolaris.opengrok.condition.ConditionalRunRule;
import org.opensolaris.opengrok.condition.RepositoryInstalled;

import static org.junit.Assert.*;

/**
 *
 * @author austvik
 */
@ConditionalRun(condition = RepositoryInstalled.GitInstalled.class)
public class GitRepositoryTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

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
     * Test of parseAnnotation method, of class GitRepository.
     * @throws java.lang.Exception
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

    @Test
    public void testDateFormats() {
        String[][] tests = new String[][]{
            {"abcd", "expected exception"},
            {"2016-01-01 10:00:00", "expected exception"},
            {"2016 Sat, 5 Apr 2008 15:12:51 +0000", "expected exception"},
            {"Sat, 5 Dub 2008 15:12:51 +0000", "expected exception"},
            {"Ned, 06 Apr 2008 15:12:51 +0730", "expected exception"},
            {"Sat, 1 Apr 2008 15:12:51 +0000", null}, // lenient - wrong date vs. day
            {"Sat, 40 Apr 2008 15:12:51 +0000", null}, // lenient - wrong day
            {"Sat, 5 Apr 2008 28:12:51 +0000", null}, // lenient - wrong hour
            {"Sat, 5 Apr 2008 15:63:51 +0000", null}, // lenient - wrong minute
            {"Sat, 5 Apr 2008 15:12:51 +0000", null},
            {"Sun, 06 Apr 2008 15:12:51 +0730", null},
            {"1 Apr 2008 15:12:51 +0300", null},
            {"2 Apr 2008 15:12:51 GMT", null}
        };

        DateFormat format = new GitRepository().getDateFormat();

        for (String[] test : tests) {
            try {
                format.parse(test[0]);
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
}
