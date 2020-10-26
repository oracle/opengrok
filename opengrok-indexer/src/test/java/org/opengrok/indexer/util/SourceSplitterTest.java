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
import org.junit.Test;
import org.opengrok.indexer.analysis.StreamSource;

/**
 * Represents a container for tests of {@link SourceSplitter}.
 */
public class SourceSplitterTest {

    @Test
    public void shouldSplitEmptyStringIntoOneLine() {
        SourceSplitter splitter = new SourceSplitter();
        splitter.reset("");
        assertEquals("split count", 1, splitter.count());
        assertEquals("split offset", 0, splitter.getOffset(0));
        assertEquals("split offset", 0, splitter.getOffset(1));

        assertEquals("split find-index", 0, splitter.findLineIndex(0));
        assertEquals("split find-index", -1, splitter.findLineIndex(1));
    }

    @Test
    public void shouldSplitEndingLFsIntoOneMoreLine() {
        SourceSplitter splitter = new SourceSplitter();
        splitter.reset("abc\ndef\n");
        assertEquals("split count", 3, splitter.count());
        assertEquals("split offset", 0, splitter.getOffset(0));
        assertEquals("split offset", 4, splitter.getOffset(1));
        assertEquals("split offset", 8, splitter.getOffset(2));
        assertEquals("split offset", 8, splitter.getOffset(3));
    }

    @Test
    public void shouldSplitDocsWithNoLastLF() {
        SourceSplitter splitter = new SourceSplitter();
        splitter.reset("abc\r\ndef");
        assertEquals("split count", 2, splitter.count());
        assertEquals("split offset", 0, splitter.getOffset(0));
        assertEquals("split offset", 5, splitter.getOffset(1));
        assertEquals("split offset", 8, splitter.getOffset(2));

        assertEquals("split find-index", 0, splitter.findLineIndex(0));
        assertEquals("split find-index", 0, splitter.findLineIndex(1));
        assertEquals("split find-index", 0, splitter.findLineIndex(4));
        assertEquals("split find-index", 1, splitter.findLineIndex(5));
        assertEquals("split find-index", 1, splitter.findLineIndex(6));
    }

    @Test
    public void shouldHandleDocsOfLongerLength() {
        //                                  0             0
        //                    0-- -  5-- - -1--- - 5--- - 2-
        final String INPUT = "ab\r\ncde\r\nefgh\r\nijk\r\nlm";

        SourceSplitter splitter = new SourceSplitter();
        splitter.reset(INPUT);
        assertEquals("split count", 5, splitter.count());
        assertEquals("split offset", 0, splitter.getOffset(0));
        assertEquals("split offset", 4, splitter.getOffset(1));
        assertEquals("split offset", 9, splitter.getOffset(2));
        assertEquals("split offset", 15, splitter.getOffset(3));
        assertEquals("split offset", 20, splitter.getOffset(4));
        assertEquals("split offset", 22, splitter.getOffset(5));

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
            assertEquals("split find-index of " + i, exp, li);
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
        assertEquals("split count", 5, splitter.count());
        assertEquals("split offset", 0, splitter.getOffset(0));
        assertEquals("split offset", 4, splitter.getOffset(1));
        assertEquals("split offset", 9, splitter.getOffset(2));
        assertEquals("split offset", 15, splitter.getOffset(3));
        assertEquals("split offset", 20, splitter.getOffset(4));
        assertEquals("split offset", 22, splitter.getOffset(5));

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
            assertEquals("split find-index of " + i, exp, li);
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
        assertEquals("split count", 11, splitter.count());
        assertEquals("split offset", 0, splitter.getOffset(0));
        assertEquals("split offset", 2, splitter.getOffset(1));
        assertEquals("split offset", 4, splitter.getOffset(2));
        assertEquals("split offset", 7, splitter.getOffset(3));
        assertEquals("split offset", 9, splitter.getOffset(4));
        assertEquals("split offset", 10, splitter.getOffset(5));
        assertEquals("split offset", 12, splitter.getOffset(6));
        assertEquals("split offset", 13, splitter.getOffset(7));
        assertEquals("split offset", 15, splitter.getOffset(8));
        assertEquals("split offset", 16, splitter.getOffset(9));
        assertEquals("split offset", 19, splitter.getOffset(10));
        assertEquals("split offset", 23, splitter.getOffset(11));
    }
}
