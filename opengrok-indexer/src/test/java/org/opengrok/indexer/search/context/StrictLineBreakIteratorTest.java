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

import org.junit.jupiter.api.Test;

import java.text.BreakIterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Represents a container for tests of {@link StrictLineBreakIterator}.
 */
class StrictLineBreakIteratorTest {

    @Test
    void testStandardLineBreakIteratorWithUnixLFs() {
        final String DOC = "abc\ndef\nghi";
        BreakIterator it = BreakIterator.getLineInstance();
        it.setText(DOC);

        assertEquals(0, it.current(), "StrictLineBreakIterator current()");
        assertEquals(4, it.next(), "StrictLineBreakIterator next()");
        assertEquals(8, it.next(), "StrictLineBreakIterator next()");
        assertEquals(11, it.next(), "StrictLineBreakIterator next()");
        assertEquals(BreakIterator.DONE, it.next(), "StrictLineBreakIterator next()");
        assertEquals(11, it.current(), "StrictLineBreakIterator current()");
    }

    @Test
    void testBreakingWithUnixLFs1() {
        final String DOC = "abc\ndef\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals(0, it.current(), "StrictLineBreakIterator current()");
        assertEquals(4, it.next(), "StrictLineBreakIterator next()");
        assertEquals(8, it.next(), "StrictLineBreakIterator next()");
        assertEquals(11, it.next(), "StrictLineBreakIterator next()");
        assertEquals(BreakIterator.DONE, it.next(), "StrictLineBreakIterator next()");
        assertEquals(11, it.current(), "StrictLineBreakIterator current()");
    }

    @Test
    void testBreakingWithUnixLFs2() {
        final String DOC = "\nabc\ndef\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals(0, it.current(), "StrictLineBreakIterator current()");
        assertEquals(1, it.next(), "StrictLineBreakIterator next()");
        assertEquals(5, it.next(), "StrictLineBreakIterator next()");
        assertEquals(9, it.next(), "StrictLineBreakIterator next()");
        assertEquals(12, it.next(), "StrictLineBreakIterator next()");
        assertEquals(BreakIterator.DONE, it.next(), "StrictLineBreakIterator next()");
        assertEquals(12, it.current(), "StrictLineBreakIterator current()");
    }

    @Test
    void testBreakingWithWindowsLFs() {
        final String DOC = "abc\r\ndef\r\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals(5, it.next(), "StrictLineBreakIterator next()");
        assertEquals(10, it.next(), "StrictLineBreakIterator next()");
        assertEquals(13, it.next(), "StrictLineBreakIterator next()");
        assertEquals(BreakIterator.DONE, it.next(), "StrictLineBreakIterator next()");
        assertEquals(13, it.current(), "StrictLineBreakIterator current()");
    }

    @Test
    void testBreakingWithMacLFs() {
        final String DOC = "abc\rdef\rghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals(4, it.next(), "StrictLineBreakIterator next()");
        assertEquals(8, it.next(), "StrictLineBreakIterator next()");
        assertEquals(11, it.next(), "StrictLineBreakIterator next()");
        assertEquals(BreakIterator.DONE, it.next(), "StrictLineBreakIterator next()");
        assertEquals(11, it.current(), "StrictLineBreakIterator current()");
    }

    @Test
    void testBreakingWithOddLFs() {
        final String DOC = "abc\n\rdef\r\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals(4, it.next(), "StrictLineBreakIterator next()");
        assertEquals(5, it.next(), "StrictLineBreakIterator next()");
        assertEquals(10, it.next(), "StrictLineBreakIterator next()");
        assertEquals(13, it.next(), "StrictLineBreakIterator next()");
        assertEquals(BreakIterator.DONE, it.next(), "StrictLineBreakIterator next()");
    }

    @Test
    void testTraversal() {
        final String DOC = "abc\ndef\nghi";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals(4, it.next(), "StrictLineBreakIterator next()");
        assertEquals(8, it.next(), "StrictLineBreakIterator next()");
        assertEquals(4, it.previous(), "StrictLineBreakIterator previous()");
        assertEquals(0, it.previous(), "StrictLineBreakIterator previous()");
        assertEquals(BreakIterator.DONE, it.previous(), "StrictLineBreakIterator previous()");
        assertEquals(4, it.next(), "StrictLineBreakIterator next()");
        assertEquals(8, it.next(), "StrictLineBreakIterator next()");
        assertEquals(11, it.next(), "StrictLineBreakIterator next()");
        assertEquals(BreakIterator.DONE, it.next(), "StrictLineBreakIterator next()");

        assertEquals(0, it.first(), "StrictLineBreakIterator first()");
        assertEquals(4, it.next(), "StrictLineBreakIterator next()");

        assertEquals(11, it.last(), "StrictLineBreakIterator last()");
        assertEquals(8, it.previous(), "StrictLineBreakIterator previous()");
    }

    @Test
    void testForEmptyString() {
        final String DOC = "";
        StrictLineBreakIterator it = new StrictLineBreakIterator();
        it.setText(DOC);

        assertEquals(BreakIterator.DONE, it.next(), "StrictLineBreakIterator next()");
        assertEquals(0, it.first(), "StrictLineBreakIterator first()");
        assertEquals(DOC.length(), it.last(), "StrictLineBreakIterator last()");
        assertEquals(BreakIterator.DONE, it.previous(), "StrictLineBreakIterator previous()");
    }
}
