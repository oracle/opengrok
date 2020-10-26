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

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Represents a container of tests of {@link PhraseHighlightComparator}.
 */
public class PhraseHighlightComparatorTest {

    private PhraseHighlight o1;
    private PhraseHighlight o2;

    @Test
    public void testEqualBoundedInstances() {
        o1 = PhraseHighlight.create(0, 1);
        o2 = PhraseHighlight.create(0, 1);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[0,1] should be == o2[0,1]", 0, result);
    }

    @Test
    public void testEqualUnboundedInstances() {
        o1 = PhraseHighlight.createEntire();
        o2 = PhraseHighlight.createEntire();
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[,] should be == o2[,]", 0, result);
    }

    @Test
    public void testEqualMixedBoundedInstances1() {
        o1 = PhraseHighlight.createStarter(5);
        o2 = PhraseHighlight.createStarter(5);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[5,] should be == o2[5,]", 0, result);
    }

    @Test
    public void testEqualMixedBoundedInstances2() {
        o1 = PhraseHighlight.createEnder(5);
        o2 = PhraseHighlight.createEnder(5);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[,5] should be == o2[,5]", 0, result);
    }

    @Test
    public void testDisjointInstances1() {
        o1 = PhraseHighlight.create(0, 10);
        o2 = PhraseHighlight.create(100, 110);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[0,10] should be < o2[100,110]", -1, result);
    }

    @Test
    public void testDisjointInstances2() {
        o1 = PhraseHighlight.create(2, 3);
        o2 = PhraseHighlight.create(0, 2);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[2,3] should be > o2[0,2]", 1, result);
    }

    @Test
    public void testBoundedOverlappingUnequalInstances1() {
        o1 = PhraseHighlight.create(0, 10);
        o2 = PhraseHighlight.create(1, 3);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[0,10] should be < o2[1,3]", -1, result);
    }

    @Test
    public void testBoundedOverlappingUnequalInstances2() {
        o1 = PhraseHighlight.create(0, 5);
        o2 = PhraseHighlight.create(0, 10);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[0,5] should be > o2[0,10]", 1, result);
    }

    @Test
    public void testBoundedOverlappingUnequalInstances3() {
        o1 = PhraseHighlight.create(5, 15);
        o2 = PhraseHighlight.create(0, 10);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[5,15] should be > o2[0,10]", 1, result);
    }

    @Test
    public void testBoundedOverlappingUnequalInstances4() {
        o1 = PhraseHighlight.create(0, 10);
        o2 = PhraseHighlight.create(5, 10);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[0,10] should be < o2[5,10]", -1, result);
    }

    @Test
    public void testMixedBoundedNonOverlappingInstances1() {
        o1 = PhraseHighlight.createEnder(10);
        o2 = PhraseHighlight.create(15, 30);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[,10] should be < o2[15,30]", -1, result);
    }

    @Test
    public void testMixedBoundedNonOverlappingInstances2() {
        o1 = PhraseHighlight.createStarter(15);
        o2 = PhraseHighlight.create(0, 10);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[15,] should be > o2[0,10]", 1, result);
    }

    @Test
    public void testMixedBoundedOverlappingInstances1() {
        o1 = PhraseHighlight.createEnder(20);
        o2 = PhraseHighlight.create(15, 30);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[,20] should be < o2[15,30]", -1, result);
    }

    @Test
    public void testMixedBoundedOverlappingInstances2() {
        o1 = PhraseHighlight.createStarter(20);
        o2 = PhraseHighlight.create(15, 30);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals("o1[20,] should be > o2[15,30]", 1, result);
    }
}
