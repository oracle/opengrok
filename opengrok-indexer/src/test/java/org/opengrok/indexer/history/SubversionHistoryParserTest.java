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
 * Copyright (c) 2006, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Ric Harris <harrisric@users.noreply.github.com>.
 */
package org.opengrok.indexer.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
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
        // Empty repository shoud produce at least valid XML.
        History result = instance.parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<log>\n" + "</log>");
        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals("Should not contain any history entries", 0, result.getHistoryEntries().size());
    }

    /**
     * Test of parsing output similar to that in subversions own svn repository.
     */
    @Test
    public void ParseALaSvn() throws Exception {
        String revId1 = "12345";
        String author1 = "username1";
        String date1 = "2007-09-11T11:48:56.123456Z";
        String file1 = "trunk/project/filename.ext";
        String revId2 = "23456";
        String author2 = "username2";
        String date2 = "2006-08-10T11:38:56.123456Z";
        String file2 = "trunk/project/path/filename2.ext2";
        String revId3 = "765432";
        String author3 = "username3";
        String date3 = "2006-08-09T10:38:56.123456Z";
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
                "<msg>* " + file2 + "\n" +
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

        String file1pref = Paths.get(Paths.get("/" + file1).toUri()).toFile().toString();
        String file2pref = Paths.get(Paths.get("/" + file2).toUri()).toFile().toString();

        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals(revId1, e1.getRevision());
        assertEquals(author1, e1.getAuthor());
        assertEquals(1, e1.getFiles().size());
        assertEquals(file1pref, e1.getFiles().first());

        HistoryEntry e2 = result.getHistoryEntries().get(1);
        assertEquals(revId2, e2.getRevision());
        assertEquals(author2, e2.getAuthor());
        assertEquals(1, e2.getFiles().size());
        assertEquals(file2pref, e2.getFiles().first());

        HistoryEntry e3 = result.getHistoryEntries().get(2);
        assertEquals(revId3, e3.getRevision());
        assertEquals(author3, e3.getAuthor());
        assertEquals(2, e3.getFiles().size());
        assertEquals(file1pref, e3.getFiles().first());
        assertEquals(file2pref, e3.getFiles().last());
        assertTrue(e3.getMessage().contains("line1"));
        assertTrue(e3.getMessage().contains("line3"));
    }

    private static class DateTimeTestData {

        private final String dateTimeString;
        private final LocalDateTime actualDateTime;
        private final boolean expectedException;

        DateTimeTestData(String dateTimeString, LocalDateTime actualDateTime) {
            this.dateTimeString = dateTimeString;
            this.actualDateTime = actualDateTime;
            this.expectedException = false;
        }

        DateTimeTestData(String dateTimeString) {
            this.dateTimeString = dateTimeString;
            this.actualDateTime = null;
            this.expectedException = true;
        }

    }


    @Test
    public void testDateFormats() {
        DateTimeTestData[] dates = new DateTimeTestData[] {
                new DateTimeTestData("2020-03-24T17:11:35.545818Z", LocalDateTime.of(2020, 3, 24, 17, 11, 35, 545000000)),
                new DateTimeTestData("2007-09-11T11:48:56.123456Z", LocalDateTime.of(2007, 9, 11, 11, 48, 56, 123000000)),
                new DateTimeTestData("2007-09-11T11:48:56.000000Z", LocalDateTime.of(2007, 9, 11, 11, 48, 56)),
                new DateTimeTestData("2007-09-11T11:48:56.Z", LocalDateTime.of(2007, 9, 11, 11, 48, 56)),
                new DateTimeTestData("2007-09-11 11:48:56Z"),
                new DateTimeTestData("2007-09-11T11:48:56"),
                new DateTimeTestData("2007-09-11T11:48:56.123456"),
                new DateTimeTestData("2007-09-11T11:48:56.000000"),
        };

        for (DateTimeTestData date : dates) {
            String revId = "12345";
            String author = "username1";
            String file = "trunk/project/filename.ext";
            try {
                String output = "<?xml version=\"1.0\"?>\n"
                        + "<log>\n"
                        + "<logentry\n"
                        + "   revision=\"" + revId + "\">\n"
                        + "<author>" + author + "</author>\n"
                        + "<date>" + date.dateTimeString + "</date>\n"
                        + "<paths>\n"
                        + "<path\n"
                        + "   action=\"M\">" + file + "</path>\n"
                        + "</paths>\n"
                        + "<msg>* " + file + "\n"
                        + "  Description.\n"
                        + "</msg>\n"
                        + "</logentry>\n"
                        + "</log>";
                History result = instance.parse(output);
                assertNotNull(result);
                assertNotNull(result.getHistoryEntries());
                assertEquals(1, result.getHistoryEntries().size());

                HistoryEntry e = result.getHistoryEntries().get(0);
                assertEquals(revId, e.getRevision());
                assertEquals(author, e.getAuthor());

                Date actualDateTime = Date.from(date.actualDateTime.atZone(ZoneOffset.systemDefault()).toInstant());
                assertEquals(date.dateTimeString, actualDateTime, e.getDate());
                assertEquals(1, e.getFiles().size());
                assertEquals(Paths.get(Paths.get("/" + file).toUri()).toFile().toString(), e.getFiles().first());
                if (date.expectedException) {
                    fail("Should throw an IO exception for " + date.dateTimeString);
                }
            } catch (IOException ex) {
                if (!date.expectedException) {
                    fail("Should not throw an IO exception for " + date.dateTimeString);
                }
            }
        }
    }
}
