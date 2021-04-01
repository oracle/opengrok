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

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.StreamSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Represents a container for tests of {@link LineBreaker}.
 */
public class LineBreakerTest {

    private static LineBreaker brkr;

    @BeforeAll
    public static void setUpClass() {
        brkr = new LineBreaker();
    }

    @Test
    public void shouldSplitEmptyStringIntoOneLine() throws IOException {
        StreamSource src = StreamSource.fromString("");
        brkr.reset(src);
        assertEquals(1, brkr.count(), "split count");
        assertEquals(0, brkr.getOffset(0), "split offset");

        assertEquals(0, brkr.findLineIndex(0), "split find-index");
        assertEquals(-1, brkr.findLineIndex(1), "split find-index");
    }

    @Test
    public void shouldSplitEndingLFsIntoOneMoreLine() throws IOException {
        StreamSource src = StreamSource.fromString("abc\ndef\n");
        brkr.reset(src);
        assertEquals(3, brkr.count(), "split count");
        assertEquals(0, brkr.getOffset(0), "split offset");
        assertEquals(4, brkr.getOffset(1), "split offset");
        assertEquals(8, brkr.getOffset(2), "split offset");
    }

    @Test
    public void shouldSplitDocsWithNoLastLF() throws IOException {
        StreamSource src = StreamSource.fromString("abc\r\ndef");
        brkr.reset(src);
        assertEquals(2, brkr.count(), "split count");
        assertEquals(0, brkr.getOffset(0), "split offset");
        assertEquals(5, brkr.getOffset(1), "split offset");
        assertEquals(8, brkr.getOffset(2), "split offset");
    }

    @Test
    public void shouldHandleDocsOfLongerLength() throws IOException {
        //                                  0             0
        //                    0-- -  5-- - -1--- - 5--- - 2-
        final String INPUT = "ab\r\ncde\r\nefgh\r\nijk\r\nlm";
        StreamSource src = StreamSource.fromString(INPUT);

        brkr.reset(src);
        assertEquals(5, brkr.count(), "split count");
        assertEquals(0, brkr.getOffset(0), "split offset");
        assertEquals(4, brkr.getOffset(1), "split offset");
        assertEquals(9, brkr.getOffset(2), "split offset");
        assertEquals(15, brkr.getOffset(3), "split offset");
        assertEquals(20, brkr.getOffset(4), "split offset");

        assertEquals(3, brkr.findLineIndex(19), "split find-index");
        assertEquals(4, brkr.findLineIndex(20), "split find-index");
        assertEquals(4, brkr.findLineIndex(21), "split find-index");
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
        assertEquals(11, brkr.count(), "split count");
        assertEquals(0, brkr.getOffset(0), "split offset");
        assertEquals(2, brkr.getOffset(1), "split offset");
        assertEquals(4, brkr.getOffset(2), "split offset");
        assertEquals(7, brkr.getOffset(3), "split offset");
        assertEquals(9, brkr.getOffset(4), "split offset");
        assertEquals(10, brkr.getOffset(5), "split offset");
        assertEquals(12, brkr.getOffset(6), "split offset");
        assertEquals(13, brkr.getOffset(7), "split offset");
        assertEquals(15, brkr.getOffset(8), "split offset");
        assertEquals(16, brkr.getOffset(9), "split offset");
        assertEquals(19, brkr.getOffset(10), "split offset");
        assertEquals(23, brkr.getOffset(11), "split offset");
    }
}
