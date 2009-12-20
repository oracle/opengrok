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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

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

}
