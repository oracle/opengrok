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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
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
        assertEquals("split position", 0, brkr.getPosition(0));
    }

    @Test
    public void shouldSplitEndingLFsIntoOneMoreLine() throws IOException {
        StreamSource src = StreamSource.fromString("abc\ndef\n");
        brkr.reset(src);
        assertEquals("split count", 3, brkr.count());
        assertEquals("split position", 0, brkr.getPosition(0));
        assertEquals("split position", 4, brkr.getPosition(1));
        assertEquals("split position", 8, brkr.getPosition(2));
    }

    @Test
    public void shouldSplitDocsWithNoLastLF() throws IOException {
        StreamSource src = StreamSource.fromString("abc\r\ndef");
        brkr.reset(src);
        assertEquals("split count", 2, brkr.count());
        assertEquals("split position", 0, brkr.getPosition(0));
        assertEquals("split position", 5, brkr.getPosition(1));
    }

    @Test
    public void shouldHandleDocsOfLongerLength() throws IOException {
        //                                  0             0
        //                    0-- -  5-- - -1--- - 5--- - 2-
        final String INPUT = "ab\r\ncde\r\nefgh\r\nijk\r\nlm";
        StreamSource src = StreamSource.fromString(INPUT);

        brkr.reset(src);
        assertEquals("split count", 5, brkr.count());
        assertEquals("split position", 0, brkr.getPosition(0));
        assertEquals("split position", 4, brkr.getPosition(1));
        assertEquals("split position", 9, brkr.getPosition(2));
        assertEquals("split position", 15, brkr.getPosition(3));
        assertEquals("split position", 20, brkr.getPosition(4));
    }
}
