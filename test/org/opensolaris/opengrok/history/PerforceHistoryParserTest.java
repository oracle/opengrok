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
        String output = "Change 1234 on 2008/10/13 by ADMIN@UserWorkspaceName 'Comment given to changelist within single qoutes, this is change one'\n" +
                "Change 6543 on 2008/10/08 by USER@USER_WS 'Comment given to changelist within single qoutes'\n" +
                "Change 7654 on 2008/09/30 by USER@USER_WS 'Comment given to changelist within single qoutes'\n" +
                "Change 2345 on 2008/09/30 by ADMIN@Workspace2 'Comment given to changelist within single qoutes'\n";
        History result = PerforceHistoryParser.parseChanges(new StringReader(output));

        assertNotNull(result);
        assertEquals(4, result.getHistoryEntries().size());

        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals("1234", e1.getRevision());
        assertEquals("ADMIN", e1.getAuthor());
        assertEquals(0, e1.getFiles().size());
        assertTrue(e1.getMessage().contains("change one"));

        HistoryEntry e4 = result.getHistoryEntries().get(3);
        assertEquals("2345", e4.getRevision());
}

    /**
     * Test of parseFileLog method, of class PerforceHistoryParser.
     */
    @Test
    public void parseFileLog() throws Exception {
        String output = "//Path/To/Folder/In/Workspace/Filename\n" +
                "\n" +
                "... #4 change 1234 edit on 2008/08/19 by User@UserWorkspaceName (text)\n" +
                "\n" +
                "        Comment for the change number 4\n" +
                "\n" +
                "... #3 change 5678 edit on 2008/08/19 by ADMIN@UserWorkspaceName (text)\n" +
                "\n" +
                "        Comment for the change\n" +
                "\n" +
                "... #2 change 8765 edit on 2008/08/01 by ADMIN@UserWorkspaceName (text)\n" +
                "\n" +
                "        Comment for the change\n" +
                "\n" +
                "... #1 change 1 add on 2008/07/30 by ADMIN@UserWorkspaceName (text)\n" +
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

        HistoryEntry e4 = result.getHistoryEntries().get(3);
        assertEquals("1", e4.getRevision());
    }
}
