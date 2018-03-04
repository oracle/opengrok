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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Calendar;
import java.util.GregorianCalendar;
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
public class RazorHistoryParserTest {

    RazorHistoryParser instance;

    public RazorHistoryParserTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance = new RazorHistoryParser();
    }

    @After
    public void tearDown() {
        instance = null;
    }

    /**
     * Test of parse method, of class RazorHistoryParser.
     */
    @Test
    public void testParseEmpty() throws Exception {
        String output = "";
        History result = instance.parseContents(new BufferedReader(new StringReader(output)));
        assertEquals(0, result.getHistoryEntries().size());
    }

    /**
     * Test of parse method, of class RazorHistoryParser.
     */
    @Test
    public void testParse() throws Exception {
        String user1 = "auser";
        String user2 = "otheruser";
        String date1 = "2009/01/23,05:56:24";
        String date2 = "2010/04/13,15:36:25";
        String date3 = "2010/05/13,15:16:26";

        String output = "INTRODUCE       " + user1 + "   1.1     Active  " + date1 + "\n" +
                "##TITLE:  Introduced file\n" +
                "##ISSUE:        I...-..3     ++ISSUES++\n" +
                "\n" +
                "CHECK-OUT       " + user2 + "   1.1     Active  " + date2 + "\n" +
                "##TITLE:  Built with gmake CC=gcc CFLAGS='-std=c99 -Wall -O2'\n" +
                "##NOTES: CHECKED OUT TO: /fileserver/sandbox/pbray/OpenGrokSampleRepository\n" +
                "##AUDIT: " + user2 + " " + date2 + " 1.1 Active CHECKED OUT TO: /fileserver/sandbox/pbray/OpenGrokSampleRepository/SimpleCProgram-BinaryRelease/sparc/testsprog \n" +
                "#The initial release was built with debugging support.\n" +
                "#This is not appropriate for the production environment.\n" +
                "##ISSUE:        I...-..5     ++ISSUES++\n" +
                "\n" +
                "EDIT_PROPS      " + user1 + "   1.1     Active  " + date2 + "\n" +
                "##TITLE:  Edit file properties\n" +
                "\n" +
                "CHECK-IN        " + user1 + "   1.2     Active  " + date3 + "\n" +
                "##TITLE:  Built with gmake CC=gcc CFLAGS='-std=c99 -Wall -O2'\n" +
                "##NOTES: CHECKED IN FROM: /fileserver/sandbox/pbray/OpenGrokSampleRepository\n" +
                "##AUDIT: " + user1 + " " + date3 + " 1.2 Active CHECKED IN FROM: /fileserver/sandbox/pbray/OpenGrokSampleRepository/SimpleCProgram-BinaryRelease/sparc/testsprog \n" +
                "#The initial release was built with debugging support.\n" +
                "#This is not appropriate for the production environment.\n" +
                "#\n" +
                "##ISSUE:        I...-..5     ++ISSUES++\n\n";
        History result = instance.parseContents(new BufferedReader(new StringReader(output)));
        assertEquals(2, result.getHistoryEntries().size());
        HistoryEntry h1 = result.getHistoryEntries().get(0);
        Calendar cal = new GregorianCalendar();
        cal.setTime(h1.getDate());
        assertEquals(2009, cal.get(Calendar.YEAR));
        assertEquals(23, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(5, cal.get(Calendar.HOUR));
        assertEquals(56, cal.get(Calendar.MINUTE));
        assertEquals(24, cal.get(Calendar.SECOND));
        assertEquals(user1, h1.getAuthor());
        assertTrue(h1.isActive());
    }

}
