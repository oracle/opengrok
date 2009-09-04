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

import java.util.Arrays;
import java.util.HashSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import static org.junit.Assert.*;

/**
 *
 * @author austvik
 */
public class BazaarHistoryParserTest {

    private BazaarHistoryParser instance;
    
    public BazaarHistoryParserTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        if (RuntimeEnvironment.getInstance().getSourceRootPath() == null) {
            RuntimeEnvironment.getInstance().setSourceRoot("");
        }
        instance = new BazaarHistoryParser(new BazaarRepository());
    }

    @After
    public void tearDown() {
        instance = null;
    }

    /**
     * Test of parse method, of class BazaarHistoryParser.
     */
    @Test
    public void parseEmpty() throws Exception {
        History result = instance.parse("");
        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertTrue("Should not contain any history entries", 0 == result.getHistoryEntries().size());        
    }

    @Test
    public void parseLogNoFile() throws Exception {
        String revId1 = "1234";
        String author1 = "First Last <username@example.com>";
        String date1 = "Wed 2008-10-01 10:01:34 +0200";

        String revId2 = "1234";
        String author2 = "First2 Last2 <username2@example.com>";
        String date2 = "Wed 2008-10-15 09:21:31 +0100";

        String revId3 = "4";
        String author3 = "First3 Last3 <username3@example.com>";
        String date3 = "Wed 2008-10-15 09:21:31 -0100";

        String revId4 = "4.1";
        String author4 = "First3 Last3 <username3@example.com>";
        String date4 = "Wed 2008-10-15 09:21:31 -0100";

        String output = "------------------------------------------------------------\n" +
                "revno: " + revId1 + "\n" +
                "committer: " + author1 + "\n" +
                "branch nick: 1.2 branch\n" +
                "timestamp: " + date1 + "\n" +
                "message:\n" +
                "  Some message.\n" +
                "------------------------------------------------------------\n" +
                "revno: " + revId2 + "\n" +
                "committer: " + author2 + "\n" +
                "branch nick: branch-name\n" +
                "timestamp: " + date2 + "\n" +
                "message:\n" +
                "  One line comment.\n" +
                "------------------------------------------------------------\n" +
                "revno: " + revId3 + "\n" +
                "committer: " + author3 + "\n" +
                "timestamp: " + date3 + "\n" +
                "message:\n" +
                "  Comment over two lines, this is line1\n" +
                "  and this is line2\n" +
                "    ------------------------------------------------------------\n" +
                "    revno: " + revId4 + "\n" +
                "    committer: " + author4 + "\n" +
                "    timestamp: " + date4 + "\n" +
                "    message:\n" +
                "      Just a message\n";

        History result = instance.parse(output);
        
        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(4, result.getHistoryEntries().size());
        
        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals(revId1, e1.getRevision());
        assertEquals(author1, e1.getAuthor());
        assertEquals(0, e1.getFiles().size());

        HistoryEntry e2 = result.getHistoryEntries().get(1);
        assertEquals(revId2, e2.getRevision());
        assertEquals(author2, e2.getAuthor());
        assertEquals(0, e2.getFiles().size());

        HistoryEntry e3 = result.getHistoryEntries().get(2);
        assertEquals(revId3, e3.getRevision());
        assertEquals(author3, e3.getAuthor());
        assertEquals(0, e3.getFiles().size());
        assertTrue(e3.getMessage().contains("line1"));
        assertTrue(e3.getMessage().contains("line2"));

        HistoryEntry e4 = result.getHistoryEntries().get(3);
        assertEquals(revId4, e4.getRevision());
        assertEquals(author4, e4.getAuthor());
        assertEquals(0, e4.getFiles().size());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void parseLogDirectory() throws Exception {
        String revId1 = "1234";
        String author1 = "username@example.com";
        String date1 = "Wed 2008-10-01 10:01:34 +0200";
        String[] files = {
            "/filename.ext",
            "/directory",
            "/directory/filename.ext",
            "/directory/filename2.ext2",
            "/otherdir/file.extension"
        };

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            output.append('-');
        }
        output.append('\n');
        output.append("revno: " + revId1 + "\n");
        output.append("committer: " + author1 + "\n");
        output.append("timestamp: " + date1 + "\n");
        output.append("message:\n");
        output.append("  Some message\n");
        output.append("added:\n");
        for (String file : files) {
            output.append("  " + file.substring(1) + "\n");
        }

        History result = instance.parse(output.toString());

        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(1, result.getHistoryEntries().size());
        
        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals(revId1, e1.getRevision());
        assertEquals(author1, e1.getAuthor());
        assertEquals(new HashSet(Arrays.asList(files)), e1.getFiles());
    }
    
}