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
 * Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.analysis.StreamSource;

/**
 * Represents a container for tests of {@link LineBreaker}.
 */
public class LineBreakerTest {

    private static LineBreaker brkr;

    @BeforeClass
    public static void setUpClass() {
        brkr = new LineBreaker();
    }

    @Test
    public void shouldSplitEmptyStringIntoOneLine() throws IOException {
        StreamSource src = StreamSource.fromString("");
        brkr.reset(src);
        assertEquals("split count", 1, brkr.count());
        assertEquals("split offset", 0, brkr.getOffset(0));

        assertEquals("split find-index", 0, brkr.findLineIndex(0));
        assertEquals("split find-index", -1, brkr.findLineIndex(1));
    }

    @Test
    public void shouldSplitEndingLFsIntoOneMoreLine() throws IOException {
        StreamSource src = StreamSource.fromString("abc\ndef\n");
        brkr.reset(src);
        assertEquals("split count", 3, brkr.count());
        assertEquals("split offset", 0, brkr.getOffset(0));
        assertEquals("split offset", 4, brkr.getOffset(1));
        assertEquals("split offset", 8, brkr.getOffset(2));
    }

    @Test
    public void shouldSplitDocsWithNoLastLF() throws IOException {
        StreamSource src = StreamSource.fromString("abc\r\ndef");
        brkr.reset(src);
        assertEquals("split count", 2, brkr.count());
        assertEquals("split offset", 0, brkr.getOffset(0));
        assertEquals("split offset", 5, brkr.getOffset(1));
        assertEquals("split offset", 8, brkr.getOffset(2));
    }

    @Test
    public void shouldHandleDocsOfLongerLength() throws IOException {
        //                                  0             0
        //                    0-- -  5-- - -1--- - 5--- - 2-
        final String INPUT = "ab\r\ncde\r\nefgh\r\nijk\r\nlm";
        StreamSource src = StreamSource.fromString(INPUT);

        brkr.reset(src);
        assertEquals("split count", 5, brkr.count());
        assertEquals("split offset", 0, brkr.getOffset(0));
        assertEquals("split offset", 4, brkr.getOffset(1));
        assertEquals("split offset", 9, brkr.getOffset(2));
        assertEquals("split offset", 15, brkr.getOffset(3));
        assertEquals("split offset", 20, brkr.getOffset(4));

        assertEquals("split find-index", 3, brkr.findLineIndex(19));
        assertEquals("split find-index", 4, brkr.findLineIndex(20));
        assertEquals("split find-index", 4, brkr.findLineIndex(21));
    }

    @Test
    public void shouldHandleInterspersedLineEndings() throws IOException {
        //                                    0                0
        //                    0- -- -5 - -- - 1 - - - -5 -- - -2--
        //                    0  1  2    3  4 5   6 7  8 9    0
        //                                                    1
        final String INPUT = "a\rb\nc\r\nd\r\r\r\n\re\n\rf\r\nghij";
        StreamSource src = StreamSource.fromString(INPUT);

        brkr.reset(src);
        assertEquals("split count", 11, brkr.count());
        assertEquals("split offset", 0, brkr.getOffset(0));
        assertEquals("split offset", 2, brkr.getOffset(1));
        assertEquals("split offset", 4, brkr.getOffset(2));
        assertEquals("split offset", 7, brkr.getOffset(3));
        assertEquals("split offset", 9, brkr.getOffset(4));
        assertEquals("split offset", 10, brkr.getOffset(5));
        assertEquals("split offset", 12, brkr.getOffset(6));
        assertEquals("split offset", 13, brkr.getOffset(7));
        assertEquals("split offset", 15, brkr.getOffset(8));
        assertEquals("split offset", 16, brkr.getOffset(9));
        assertEquals("split offset", 19, brkr.getOffset(10));
        assertEquals("split offset", 23, brkr.getOffset(11));
    }
}
