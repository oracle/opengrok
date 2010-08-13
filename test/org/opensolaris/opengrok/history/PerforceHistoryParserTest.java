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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.history;

import java.io.StringReader;
import java.util.Calendar;
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
public class PerforceHistoryParserTest {

    public PerforceHistoryParserTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of parseChanges method, of class PerforceHistoryParser.
     */
    @Test
    public void parseChanges() throws Exception {
        String output = "Change 1234 on 2008/10/13 11:30:00 by ADMIN@UserWorkspaceName 'Comment given to changelist within single qoutes, this is change one'\n" +
                "Change 6543 on 2008/10/08 18:25:38 by USER@USER_WS 'Comment given to changelist within single qoutes'\n" +
                "Change 7654 on 2008/09/30 01:00:01 by USER@USER_WS 'Comment given to changelist within single qoutes'\n" +
                "Change 2345 on 2008/09/30 17:45:33 by ADMIN@Workspace2 'Comment given to changelist within single qoutes'\n";
        History result = PerforceHistoryParser.parseChanges(new StringReader(output));

        assertNotNull(result);
        assertEquals(4, result.getHistoryEntries().size());

        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals("1234", e1.getRevision());
        assertEquals("ADMIN", e1.getAuthor());
        assertEquals(0, e1.getFiles().size());
        assertTrue(e1.getMessage().contains("change one"));

        HistoryEntry e2 = result.getHistoryEntries().get(1);
        assertNotNull(e2);

        HistoryEntry e3 = result.getHistoryEntries().get(2);
        assertNotNull(e3);

        HistoryEntry e4 = result.getHistoryEntries().get(3);
        assertEquals("2345", e4.getRevision());

        // Bug #16660: Months used to be off by one. Verify that they match
        // the dates in the sample output above.
        assertDate(e1, 2008, Calendar.OCTOBER, 13);
        assertDate(e2, 2008, Calendar.OCTOBER, 8);
        assertDate(e3, 2008, Calendar.SEPTEMBER, 30);
        assertDate(e4, 2008, Calendar.SEPTEMBER, 30);
    }

    /**
     * Test of parseFileLog method, of class PerforceHistoryParser.
     */
    @Test
    public void parseFileLog() throws Exception {
        String output = "//Path/To/Folder/In/Workspace/Filename\n" +
                "\n" +
                "... #4 change 1234 edit on 2008/08/19 11:30:00 by User@UserWorkspaceName (text)\n" +
                "\n" +
                "        Comment for the change number 4\n" +
                "\n" +
                "... #3 change 5678 edit on 2008/08/19 18:25:38 by ADMIN@UserWorkspaceName (text)\n" +
                "\n" +
                "        Comment for the change\n" +
                "\n" +
                "... #2 change 8765 edit on 2008/08/01 01:00:01 by ADMIN@UserWorkspaceName (text)\n" +
                "\n" +
                "        Comment for the change\n" +
                "\n" +
                "... #1 change 1 add on 2008/07/30 17:45:33 by ADMIN@UserWorkspaceName (text)\n" +
                "\n" +
                "        Comment for the change";

        History result = PerforceHistoryParser.parseFileLog(new StringReader(output));

        assertNotNull(result);
        assertEquals(4, result.getHistoryEntries().size());

        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals("4", e1.getRevision());
        assertEquals("User", e1.getAuthor());
        assertEquals(0, e1.getFiles().size());
        assertTrue(e1.getMessage().contains("number 4"));

        HistoryEntry e2 = result.getHistoryEntries().get(1);
        assertNotNull(e2);

        HistoryEntry e3 = result.getHistoryEntries().get(2);
        assertNotNull(e3);

        HistoryEntry e4 = result.getHistoryEntries().get(3);
        assertEquals("1", e4.getRevision());

        // Bug #16660: Months used to be off by one. Verify that they match
        // the dates in the sample output above.
        assertDate(e1, 2008, Calendar.AUGUST, 19);
        assertDate(e2, 2008, Calendar.AUGUST, 19);
        assertDate(e3, 2008, Calendar.AUGUST, 1);
        assertDate(e4, 2008, Calendar.JULY, 30);
    }

    /**
     * Assert that the date of a history entry is as expected.
     *
     * @param entry the history entry to check
     * @param year the expected year
     * @param month the expected month (note: January is 0, not 1)
     * @param day the expected day of the month
     */
    private static void assertDate(
            HistoryEntry entry, int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(entry.getDate());
        assertEquals("year", year, cal.get(Calendar.YEAR));
        assertEquals("month", month, cal.get(Calendar.MONTH));
        assertEquals("day", day, cal.get(Calendar.DAY_OF_MONTH));
    }
}
