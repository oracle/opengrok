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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author austvik
 */
class CVSHistoryParserTest {

    CVSHistoryParser instance;

    @BeforeEach
    public void setUp() {
        instance = new CVSHistoryParser();
    }

    @AfterEach
    public void tearDown() {
        instance = null;
    }

    /**
     * Test of parse method, of class CVSHistoryParser.
     * @throws Exception exception
     */
    @Test
    void parseEmpty() throws Exception {
        History result = instance.parse("");
        assertNotNull(result);
        assertEquals(0, result.getHistoryEntries().size(), "Should not contain any history entries");
    }

    /**
     * Parse something that could come out from the W3C public CVS repository.
     */
    @Test
    void parseALaW3C() throws Exception {
        String revId1 = "1.2";
        String revId2 = "1.2.4.5";
        String revId3 = "1.134";
        String tag1 = "file_tag";
        String author1 = "username";
        String author2 = "username2";
        String date1 = "2005-05-16 21:22:34 +0200";
        String date2 = "2007-05-16 22:21:30 +0300";
        String output = "\n" +
                "\n" +
                "  RCS file: /some/file/name.ext,v\n" +
                "Working file: name.ext\n" +
                "head: 1.23\n" +
                "branch:\n" +
                "locks: strict\n" +
                "access list:\n" +
                "symbolic names:\n" +
                "\t" + tag1 + ": " + revId2 + "\n" +
                "keyword substitution: kv\n" +
                "total revisions: 4;\tselected revisions: 3\n" +
                "description:\n" +
                "----------------------------\n" +
                "revision " + revId1 + "\n" +
                "date: " + date1 + ";  author: " + author1 +
                ";  state: Exp;  lines: +2 -2;\n" +
                "a comment\n" +
                "----------------------------\n" +
                "revision " + revId2 + "\n" +
                "date: " + date2 + ";  author: " + author2 +
                ";  state: Exp;  lines: +2 -4;\n" +
                "just a short comment\n" +
                "----------------------------\n" +
                "revision " + revId3 + "\n" +
                "date: " + date1 + ";  author: " + author1 +
                ";  state: Exp;  lines: +6 -2;\n" +
                "some comment that is\n" +
                "--------\n" +
                "spanning multiple lines\n" +
                "==========================================================" +
                "===================\n";
        History result = instance.parse(output);
        assertNotNull(result);
        assertEquals(3, result.getHistoryEntries().size());
        HistoryEntry e0 = result.getHistoryEntries().get(0);
        assertEquals(revId1, e0.getRevision());
        assertEquals(author1, e0.getAuthor());
        assertEquals(0, e0.getFiles().size());
        HistoryEntry e1 = result.getHistoryEntries().get(1);
        assertEquals(revId2, e1.getRevision());
        assertEquals(author2, e1.getAuthor());
        assertEquals(0, e1.getFiles().size());
        HistoryEntry e2 = result.getHistoryEntries().get(2);
        assertEquals(revId3, e2.getRevision());
        assertEquals(author1, e2.getAuthor());
        assertEquals(0, e2.getFiles().size());
        assertTrue(e2.getMessage().contains("some"), "Should contain comment of both lines: line 1");
        assertTrue(e2.getMessage().contains("multiple"), "Should contain comment of both lines: line 2");

        assertEquals(Map.of(revId2, tag1), result.getTags());
    }
}
