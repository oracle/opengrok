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
import static org.junit.Assert.assertEquals;
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
}
