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

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.StreamSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Represents a container for tests of {@link SourceSplitter}.
 */
public class SourceSplitterTest {

    @Test
    public void shouldSplitEmptyStringIntoOneLine() {
        SourceSplitter splitter = new SourceSplitter();
        splitter.reset("");
        assertEquals(1, splitter.count(), "split count");
        assertEquals(0, splitter.getOffset(0), "split offset");
        assertEquals(0, splitter.getOffset(1), "split offset");

        assertEquals(0, splitter.findLineIndex(0), "split find-index");
        assertEquals(-1, splitter.findLineIndex(1), "split find-index");
    }

    @Test
    public void shouldSplitEndingLFsIntoOneMoreLine() {
        SourceSplitter splitter = new SourceSplitter();
        splitter.reset("abc\ndef\n");
        assertEquals(3, splitter.count(), "split count");
        assertEquals(0, splitter.getOffset(0), "split offset");
        assertEquals(4, splitter.getOffset(1), "split offset");
        assertEquals(8, splitter.getOffset(2), "split offset");
        assertEquals(8, splitter.getOffset(3), "split offset");
    }

    @Test
    public void shouldSplitDocsWithNoLastLF() {
        SourceSplitter splitter = new SourceSplitter();
        splitter.reset("abc\r\ndef");
        assertEquals(2, splitter.count(), "split count");
        assertEquals(0, splitter.getOffset(0), "split offset");
        assertEquals(5, splitter.getOffset(1), "split offset");
        assertEquals(8, splitter.getOffset(2), "split offset");

        assertEquals(0, splitter.findLineIndex(0), "split find-index");
        assertEquals(0, splitter.findLineIndex(1), "split find-index");
        assertEquals(0, splitter.findLineIndex(4), "split find-index");
        assertEquals(1, splitter.findLineIndex(5), "split find-index");
        assertEquals(1, splitter.findLineIndex(6), "split find-index");
    }

    @Test
    public void shouldHandleDocsOfLongerLength() {
        //                                  0             0
        //                    0-- -  5-- - -1--- - 5--- - 2-
        final String INPUT = "ab\r\ncde\r\nefgh\r\nijk\r\nlm";

        SourceSplitter splitter = new SourceSplitter();
        splitter.reset(INPUT);
        assertEquals(5, splitter.count(), "split count");
        assertEquals(0, splitter.getOffset(0), "split offset");
        assertEquals(4, splitter.getOffset(1), "split offset");
        assertEquals(9, splitter.getOffset(2), "split offset");
        assertEquals(15, splitter.getOffset(3), "split offset");
        assertEquals(20, splitter.getOffset(4), "split offset");
        assertEquals(22, splitter.getOffset(5), "split offset");

        /*
         * Test findLineIndex() for every character with an alternate
         * computation that counts every LF.
         */
        for (int i = 0; i < splitter.originalLength(); ++i) {
            char c = INPUT.charAt(i);
            int li = splitter.findLineIndex(i);
            long numLF = INPUT.substring(0, i + 1).chars().filter(ch ->
                ch == '\n').count();
            long exp = numLF - (c == '\n' ? 1 : 0);
            assertEquals(exp, li, "split find-index of " + i);
        }
    }

    @Test
    public void shouldHandleStreamedDocsOfLongerLength() throws IOException {
        //                                  0             0
        //                    0-- -  5-- - -1--- - 5--- - 2-
        final String INPUT = "ab\r\ncde\r\nefgh\r\nijk\r\nlm";
        StreamSource src = StreamSource.fromString(INPUT);

        SourceSplitter splitter = new SourceSplitter();
        splitter.reset(src);
        assertEquals(5, splitter.count(), "split count");
        assertEquals(0, splitter.getOffset(0), "split offset");
        assertEquals(4, splitter.getOffset(1), "split offset");
        assertEquals(9, splitter.getOffset(2), "split offset");
        assertEquals(15, splitter.getOffset(3), "split offset");
        assertEquals(20, splitter.getOffset(4), "split offset");
        assertEquals(22, splitter.getOffset(5), "split offset");

        /*
         * Test findLineIndex() for every character with an alternate
         * computation that counts every LF.
         */
        for (int i = 0; i < splitter.originalLength(); ++i) {
            char c = INPUT.charAt(i);
            int li = splitter.findLineIndex(i);
            long numLF = INPUT.substring(0, i + 1).chars().filter(ch ->
                ch == '\n').count();
            long exp = numLF - (c == '\n' ? 1 : 0);
            assertEquals(exp, li, "split find-index of " + i);
        }
    }

    @Test
    public void shouldHandleInterspersedLineEndings() throws IOException {
        //                                    0                0
        //                    0- -- -5 - -- - 1 - - - -5 -- - -2--
        //                    0  1  2    3  4 5   6 7  8 9    0
        //                                                    1
        final String INPUT = "a\rb\nc\r\nd\r\r\r\n\re\n\rf\r\nghij";
        StreamSource src = StreamSource.fromString(INPUT);

        SourceSplitter splitter = new SourceSplitter();
        splitter.reset(src);
        assertEquals(11, splitter.count(), "split count");
        assertEquals(0, splitter.getOffset(0), "split offset");
        assertEquals(2, splitter.getOffset(1), "split offset");
        assertEquals(4, splitter.getOffset(2), "split offset");
        assertEquals(7, splitter.getOffset(3), "split offset");
        assertEquals(9, splitter.getOffset(4), "split offset");
        assertEquals(10, splitter.getOffset(5), "split offset");
        assertEquals(12, splitter.getOffset(6), "split offset");
        assertEquals(13, splitter.getOffset(7), "split offset");
        assertEquals(15, splitter.getOffset(8), "split offset");
        assertEquals(16, splitter.getOffset(9), "split offset");
        assertEquals(19, splitter.getOffset(10), "split offset");
        assertEquals(23, splitter.getOffset(11), "split offset");
    }
}
