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
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Unit tests for the ExpandTabsReader class.
 */
public class ExpandTabsReaderTest {

    /**
     * Test that tabs are expanded to spaces.
     */
    @Test
    public void testExpandTabs() throws IOException {
        // Create a couple of lines to see if tabs are expanded as expected.
        String inputLine = "abc\tdef\t\t12345678\t1\t1234567\tabc";
        StringBuilder input = new StringBuilder();
        input.append(inputLine).append('\n');
        input.append(inputLine).append('\r');
        input.append('\t');

        // Create Reader that reads the test input.
        StringReader sr = new StringReader(input.toString());

        // Wrap the input in an ExpandTabsReader with tab size 8.
        Reader expandedInput = new ExpandTabsReader(sr, 8);

        // Here's what inputLine should be expanded to.
        String expectedLine =
                "abc     def             12345678        1       1234567 abc";

        // Verify that tabs are expanded.
        BufferedReader br = new BufferedReader(expandedInput);
        assertEquals(expectedLine, br.readLine());
        assertEquals(expectedLine, br.readLine());
        assertEquals("        ", br.readLine());
        assertNull(br.readLine());
    }

    /**
     * Test that skip() works over tabs.
     */
    @Test
    public void testSkip() throws IOException {
        Reader r = new ExpandTabsReader(new StringReader("\txyz"), 8);

        // Skip four characters. That is, half of the tab after expansion.
        long toSkip = 4;
        while (toSkip > 0) {
            long skipped = r.skip(toSkip);
            assertTrue(skipped > 0);
            assertTrue(skipped <= toSkip);
            toSkip -= skipped;
        }

        // What's left in the Reader?
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = r.read()) != -1) {
            sb.append((char) c);
        }

        assertEquals("    xyz", sb.toString());
    }

    /**
     * Tests that line offsets are translated as expected.
     */
    @Test
    public void testTranslate() {
        final String INPUT = "abc\tdef\t\t12345678\t1\t1234567\tabc";

        int tbsz = 8;
        assertEquals(0,  ExpandTabsReader.translate(INPUT, 0,  tbsz)); // a
        assertEquals(2,  ExpandTabsReader.translate(INPUT, 2,  tbsz)); // c
        assertEquals(3,  ExpandTabsReader.translate(INPUT, 3,  tbsz)); // \t
        assertEquals(8,  ExpandTabsReader.translate(INPUT, 4,  tbsz)); // d
        assertEquals(10, ExpandTabsReader.translate(INPUT, 6,  tbsz)); // f
        assertEquals(11, ExpandTabsReader.translate(INPUT, 7,  tbsz)); // \t
        assertEquals(16, ExpandTabsReader.translate(INPUT, 8,  tbsz)); // \t
        assertEquals(24, ExpandTabsReader.translate(INPUT, 9,  tbsz)); // 1
        assertEquals(31, ExpandTabsReader.translate(INPUT, 16, tbsz)); // 8
        assertEquals(32, ExpandTabsReader.translate(INPUT, 17, tbsz)); // \t
        assertEquals(40, ExpandTabsReader.translate(INPUT, 18, tbsz)); // 1
        assertEquals(41, ExpandTabsReader.translate(INPUT, 19, tbsz)); // \t
        assertEquals(48, ExpandTabsReader.translate(INPUT, 20, tbsz)); // 1
        assertEquals(54, ExpandTabsReader.translate(INPUT, 26, tbsz)); // 7
        assertEquals(55, ExpandTabsReader.translate(INPUT, 27, tbsz)); // \t
        assertEquals(56, ExpandTabsReader.translate(INPUT, 28, tbsz)); // a
    }
}
