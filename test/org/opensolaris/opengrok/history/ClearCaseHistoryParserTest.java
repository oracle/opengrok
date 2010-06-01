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
public class ClearCaseHistoryParserTest {
    
    private ClearCaseHistoryParser instance;

    public ClearCaseHistoryParserTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance = new ClearCaseHistoryParser();
    }

    @After
    public void tearDown() {
        instance = null;
    }

    /**
     * Test of parse method, of class ClearCaseHistoryParser.
     */
    @Test
    public void parseFileHistory() throws Exception {
        String author1 = "First Last (username)";
        String author2 = "First2 Last2 (username2)";
        String output = "create version\n" +
                "20020618.212343\n" +
                author1 + "\n" +
                "/main/3\n" +
                "Merge from eeeee for ffffff\n" +
                ".\n" +
                "create version\n" +
                "20020222.164239\n" +
                author2 + "\n" +
                "/main/2\n" +
                "Merge from projper branch.\n" +
                "Adding aaaaaaa to the yyyyy.\n" +
                ".\n" +
                "create version\n" +
                "20020208.150825\n" +
                author2 + "\n" +
                "/main/1\n" +
                "Target for javac set to 1.3.\n" +
                "Fixed handling of " +
                " res directory.\n" +
                ".\n" +
                "create version\n" +
                "20010924.095104\n" +
                author2 + "\n" +
                "/main/0\n" +
                "\n" +
                ".\n" +
                "create branch\n" +
                "20010924.095104\n" +
                author2 + "\n" +
                "/main\n" +
                "\n" +
                ".\n" +
                "create file element\n" +
                "20010924.095104\n" +
                author1 + "\n" +
                "\n" +
                "\n" +
                ".";
        
        History result = instance.parse(output);
        assertNotNull(result);
        assertEquals(4, result.getHistoryEntries().size());
        
        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals("/main/3", e1.getRevision());
        assertEquals(author1, e1.getAuthor());
        assertEquals(0, e1.getFiles().size());
        assertTrue(e1.getMessage().contains("eeeee"));

        HistoryEntry e4 = result.getHistoryEntries().get(3);
        assertEquals("/main/0", e4.getRevision());
        assertEquals(author2, e4.getAuthor());
        assertEquals(0, e4.getFiles().size());
    }
    
   /**
     * Test of parse method, of class ClearCaseHistoryParser.
     */
    @Test
    public void parseDirHistory() throws Exception {
        String author1 = "First Last (username)";
        String author2 = "First2 Last2 (username2)";
        String output = "create directory version\n" +
                "20050401.162902\n" +
                author1 + "\n" +
                "/main/3\n" +
                "Removed directory element \"prototype\".\n" +
                ".\n" +
                "create directory version\n" +
                "20020618.215917\n" +
                author2 + "\n" +
                "/main/2\n" +
                "Merge from wwwww for dddddd\n" +
                ".\n" +
                "create directory version\n" +
                "20010228.174617\n" +
                author1 + "\n" +
                "/main/1\n" +
                "New structure.\n" +
                "\n" +
                "Added directory element \"generic\".\n" +
                "Added directory element \"prototype\".\n" +
                "Added directory element \"install\".\n" +
                "Added directory element \"service\".\n" +
                ".\n" +
                "create directory version\n" +
                "20001215.092522\n" +
                author2 + "\n" +
                "/main/0\n" +
                "\n" +
                ".\n" +
                "create branch\n" +
                "20001215.092522\n" +
                author1 + "\n" +
                "/main\n" +
                "\n" +
                ".\n" +
                "create directory element\n" +
                "20001215.092522\n" +
                author1 + "\n" +
                "\n" +
                "\n" +
                ".";
        History result = instance.parse(output);
        assertNotNull(result);
        assertEquals(4, result.getHistoryEntries().size());

        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals("/main/3", e1.getRevision());
        assertEquals(author1, e1.getAuthor());
        assertEquals(0, e1.getFiles().size());
        assertTrue(e1.getMessage().contains("prototype"));

        HistoryEntry e4 = result.getHistoryEntries().get(3);
        assertEquals("/main/0", e4.getRevision());
        assertEquals(author2, e4.getAuthor());
        assertEquals(0, e4.getFiles().size());
    }

}
