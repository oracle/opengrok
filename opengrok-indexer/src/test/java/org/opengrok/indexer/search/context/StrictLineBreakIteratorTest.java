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
package org.opengrok.indexer.search.context;

import java.text.BreakIterator;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Represents a container for tests of {@link StrictLineBreakIterator}.
 */
public class StrictLineBreakIteratorTest {

    @Test
    public void testStandardLineBreakIteratorWithUnixLFs() {
        final String DOC = "abc\ndef\nghi";
        BreakIterator it = BreakIterator.getLineInstance();
        it.setText(DOC);

        assertEquals("StrictLineBreakIterator current()", 0, it.current());
        assertEquals("StrictLineBreakIterator next()", 4, it.next());
        assertEquals("StrictLineBreakIterator next()", 8, it.next());
        assertEquals("StrictLineBreakIterator next()", 11, it.next());
        assertEquals("StrictLineBreakIterator next()", BreakIterator.DONE,
            it.next());
        assertEquals("StrictLineBreakIterator current()", 11, it.current());
    }

    @Test
    public void testBreakingWithUnixLFs1() {
        final String DOC = "abc\ndef\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals("StrictLineBreakIterator current()", 0, it.current());
        assertEquals("StrictLineBreakIterator next()", 4, it.next());
        assertEquals("StrictLineBreakIterator next()", 8, it.next());
        assertEquals("StrictLineBreakIterator next()", 11, it.next());
        assertEquals("StrictLineBreakIterator next()", BreakIterator.DONE,
            it.next());
        assertEquals("StrictLineBreakIterator current()", 11, it.current());
    }

    @Test
    public void testBreakingWithUnixLFs2() {
        final String DOC = "\nabc\ndef\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals("StrictLineBreakIterator current()", 0, it.current());
        assertEquals("StrictLineBreakIterator next()", 1, it.next());
        assertEquals("StrictLineBreakIterator next()", 5, it.next());
        assertEquals("StrictLineBreakIterator next()", 9, it.next());
        assertEquals("StrictLineBreakIterator next()", 12, it.next());
        assertEquals("StrictLineBreakIterator next()", BreakIterator.DONE,
            it.next());
        assertEquals("StrictLineBreakIterator current()", 12, it.current());
    }

    @Test
    public void testBreakingWithWindowsLFs() {
        final String DOC = "abc\r\ndef\r\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals("StrictLineBreakIterator next()", 5, it.next());
        assertEquals("StrictLineBreakIterator next()", 10, it.next());
        assertEquals("StrictLineBreakIterator next()", 13, it.next());
        assertEquals("StrictLineBreakIterator next()", BreakIterator.DONE,
            it.next());
        assertEquals("StrictLineBreakIterator current()", 13, it.current());
    }

    @Test
    public void testBreakingWithMacLFs() {
        final String DOC = "abc\rdef\rghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals("StrictLineBreakIterator next()", 4, it.next());
        assertEquals("StrictLineBreakIterator next()", 8, it.next());
        assertEquals("StrictLineBreakIterator next()", 11, it.next());
        assertEquals("StrictLineBreakIterator next()", BreakIterator.DONE,
            it.next());
        assertEquals("StrictLineBreakIterator current()", 11, it.current());
    }

    @Test
    public void testBreakingWithOddLFs() {
        final String DOC = "abc\n\rdef\r\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals("StrictLineBreakIterator next()", 4, it.next());
        assertEquals("StrictLineBreakIterator next()", 5, it.next());
        assertEquals("StrictLineBreakIterator next()", 10, it.next());
        assertEquals("StrictLineBreakIterator next()", 13, it.next());
        assertEquals("StrictLineBreakIterator next()", BreakIterator.DONE,
            it.next());
    }

    @Test
    public void testTraversal() {
        final String DOC = "abc\ndef\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals("StrictLineBreakIterator next()", 4, it.next());
        assertEquals("StrictLineBreakIterator next()", 8, it.next());
        assertEquals("StrictLineBreakIterator previous()", 4, it.previous());
        assertEquals("StrictLineBreakIterator previous()", 0, it.previous());
        assertEquals("StrictLineBreakIterator previous()", BreakIterator.DONE,
            it.previous());
        assertEquals("StrictLineBreakIterator next()", 4, it.next());
        assertEquals("StrictLineBreakIterator next()", 8, it.next());
        assertEquals("StrictLineBreakIterator next()", 11, it.next());
        assertEquals("StrictLineBreakIterator next()", BreakIterator.DONE,
            it.next());

        assertEquals("StrictLineBreakIterator first()", 0, it.first());
        assertEquals("StrictLineBreakIterator next()", 4, it.next());

        assertEquals("StrictLineBreakIterator last()", 11, it.last());
        assertEquals("StrictLineBreakIterator previous()", 8, it.previous());
    }

    @Test
    public void testForEmptyString() {
        final String DOC = "";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals("StrictLineBreakIterator next()", BreakIterator.DONE,
            it.next());
        assertEquals("StrictLineBreakIterator first()", 0, it.first());
        assertEquals("StrictLineBreakIterator last()", DOC.length(), it.last());
        assertEquals("StrictLineBreakIterator previous()", BreakIterator.DONE,
            it.previous());
    }
}
