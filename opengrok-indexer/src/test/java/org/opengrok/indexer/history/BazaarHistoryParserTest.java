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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author austvik
 */
class BazaarHistoryParserTest {

    private BazaarHistoryParser instance;

    @BeforeEach
    void setUp() {
        if (RuntimeEnvironment.getInstance().getSourceRootPath() == null) {
            RuntimeEnvironment.getInstance().setSourceRoot("");
        }
        BazaarRepository bzrRepo = new BazaarRepository();
        // BazaarHistoryParser needs to have a valid directory name.
        bzrRepo.setDirectoryNameRelative("bzrRepo");
        instance = new BazaarHistoryParser(bzrRepo);
    }

    @AfterEach
    void tearDown() {
        instance = null;
    }

    /**
     * Test of parse method, of class BazaarHistoryParser.
     * @throws Exception exception
     */
    @Test
    void parseEmpty() throws Exception {
        History result = instance.parse("");
        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(0, result.getHistoryEntries().size(), "Should not contain any history entries");
    }

    @Test
    void parseLogNoFile() throws Exception {
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
        assertEquals(3, result.getHistoryEntries().size());

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
        assertTrue(e3.getMessage().contains("revno: " + revId4));
    }

    @Test
    void parseLogDirectory() throws Exception {
        String revId1 = "1234";
        String author1 = "username@example.com";
        String date1 = "Wed 2008-10-01 10:01:34 +0200";
        String[] files;
        if (SystemUtils.IS_OS_WINDOWS) {
            files = new String[] {
                    "\\\\filename.ext",
                    "\\\\directory",
                    "\\\\directory\\filename.ext",
                    "\\\\directory\\filename2.ext2",
                    "\\\\otherdir\\file.extension"
            };
        } else {
            files = new String[] {
                    "/filename.ext",
                    "/directory",
                    "/directory/filename.ext",
                    "/directory/filename2.ext2",
                    "/otherdir/file.extension"
            };
        }

        StringBuilder output = new StringBuilder();
        output.append("-".repeat(60));
        output.append('\n');
        output.append("revno: ").append(revId1).append("\n");
        output.append("committer: ").append(author1).append("\n");
        output.append("timestamp: ").append(date1).append("\n");
        output.append("message:\n");
        output.append("  Some message\n");
        output.append("added:\n");
        for (String file : files) {
            output.append("  ").append(file.substring(1)).append("\n");
        }

        History result = instance.parse(output.toString());

        assertNotNull(result);
        assertNotNull(result.getHistoryEntries());
        assertEquals(1, result.getHistoryEntries().size());

        HistoryEntry e1 = result.getHistoryEntries().get(0);
        assertEquals(revId1, e1.getRevision());
        assertEquals(author1, e1.getAuthor());
        assertEquals(new HashSet<>(Arrays.asList(files)), e1.getFiles());
    }

}
