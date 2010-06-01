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
public class SubversionHistoryParserTest {

    private SubversionHistoryParser instance;
    
    public SubversionHistoryParserTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance = new SubversionHistoryParser();
    }

    @After
    public void tearDown() {
        instance = null;
    }

    /**
     * Test of parse method, of class SubversionHistoryParser.
     */
    @Test
    public void parseEmpty() throws Exception {
        History result = instance.parse("");
        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertTrue("Should not contain any history entries", 0 == result.getHistoryEntries().size());
    }
    
    /**
     * Test of parsing output similar to that in subversions own svn repository.
     */
    @Test 
    public void ParseALaSvn() throws Exception {
        String revId1 = "12345";
        String author1 = "username1";
        String date1= "2007-09-11T11:48:56.123456Z";
        String file1 = "trunk/project/filename.ext";
        String revId2 = "23456";
        String author2 = "username2";
        String date2= "2006-08-10T11:38:56.123456Z";
        String file2 = "trunk/project/path/filename2.ext2";
        String revId3 = "765432";
        String author3 = "username3";
        String date3= "2006-08-09T10:38:56.123456Z";
        String output = "<?xml version=\"1.0\"?>\n" +
                "<log>\n" +
                "<logentry\n" +
                "   revision=\"" + revId1 + "\">\n" +
                "<author>" + author1 + "</author>\n" +
                "<date>" + date1 + "</date>\n" +
                "<paths>\n" +
                "<path\n" +
                "   action=\"M\">" + file1 + "</path>\n" +
                "</paths>\n" +
                "<msg>* " + file1 + "\n" +
                "  Description.\n" +
                "</msg>\n" +
                "</logentry>\n" +
                "<logentry\n" +
                "   revision=\"" + revId2 + "\">\n" +
                "<author>" + author2 + "</author>\n" +
                "<date>" + date2 + "</date>\n" +
                "<paths>\n" +
                "<path\n" +
                "   action=\"M\">" + file2 + "</path>\n" +
                "</paths>\n" +
                "<msg>* " + file2 +"\n" +
                "  some comment\n" +
                "  over several lines.\n" +
                "</msg>\n" +
                "</logentry>\n" +
                "<logentry\n" +
                "   revision=\"" + revId3 + "\">\n" +
                "<author>" + author3 + "</author>\n" +
                "<date>" + date3 + "</date>\n" +
                "<paths>\n" +
                "<path\n" +
                "   action=\"M\">" + file1 + "</path>\n" +
                "<path\n" +
                "   action=\"A\">" + file2 + "</path>\n" +
                "</paths>\n" +
                "<msg>this is a longer comment - line1\n" +
                "    that spans some lines,\n" +
                "    three in fact - line3.\n" +
                "</msg>\n" +
                "</logentry>\n" +
                "</log>";
        History result = instance.parse(output);
        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(3, result.getHistoryEntries().size());
        
        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals(revId1, e1.getRevision());
        assertEquals(author1, e1.getAuthor());
        assertEquals(1, e1.getFiles().size());
        assertEquals("/" + file1, e1.getFiles().first());

        HistoryEntry e2 = result.getHistoryEntries().get(1);
        assertEquals(revId2, e2.getRevision());
        assertEquals(author2, e2.getAuthor());
        assertEquals(1, e2.getFiles().size());
        assertEquals("/" + file2, e2.getFiles().first());

        HistoryEntry e3 = result.getHistoryEntries().get(2);
        assertEquals(revId3, e3.getRevision());
        assertEquals(author3, e3.getAuthor());
        assertEquals(2, e3.getFiles().size());
        assertEquals("/" + file1, e3.getFiles().first());
        assertEquals("/" + file2, e3.getFiles().last());
        assertTrue(e3.getMessage().contains("line1"));
        assertTrue(e3.getMessage().contains("line3"));
    }

}
