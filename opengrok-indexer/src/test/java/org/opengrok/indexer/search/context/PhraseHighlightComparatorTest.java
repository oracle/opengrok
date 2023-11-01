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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Represents a container of tests of {@link PhraseHighlightComparator}.
 */
class PhraseHighlightComparatorTest {


    @Test
    void testEqualBoundedInstances() {
        var o1 = PhraseHighlight.create(0, 1);
        var o2 = PhraseHighlight.create(0, 1);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(0, result, "o1[0,1] should be == o2[0,1]");
    }

    @Test
    void testEqualUnboundedInstances() {
        var o1 = PhraseHighlight.createEntire();
        var o2 = PhraseHighlight.createEntire();
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(0, result, "o1[,] should be == o2[,]");
    }

    @Test
    void testEqualMixedBoundedInstances1() {
        var o1 = PhraseHighlight.createStarter(5);
        var o2 = PhraseHighlight.createStarter(5);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(0, result, "o1[5,] should be == o2[5,]");
    }

    @Test
    void testEqualMixedBoundedInstances2() {
        var o1 = PhraseHighlight.createEnder(5);
        var o2 = PhraseHighlight.createEnder(5);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(0, result, "o1[,5] should be == o2[,5]");
    }

    @Test
    void testDisjointInstances1() {
        var o1 = PhraseHighlight.create(0, 10);
        var o2 = PhraseHighlight.create(100, 110);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(-1, result, "o1[0,10] should be < o2[100,110]");
    }

    @Test
    void testDisjointInstances2() {
        var o1 = PhraseHighlight.create(2, 3);
        var o2 = PhraseHighlight.create(0, 2);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(1, result, "o1[2,3] should be > o2[0,2]");
    }

    @ParameterizedTest
    @MethodSource("dataLessThan")
    void testBoundedOverlappingUnequalFirstLessThanSecondInstances(int @NotNull [] o1, int @NotNull [] o2) {
        var objPhraseHighlight1 =  PhraseHighlight.create(o1[0], o1[1]);
        var objPhraseHighlight2 =  PhraseHighlight.create(o2[0], o2[1]);
        int result = PhraseHighlightComparator.INSTANCE.compare(objPhraseHighlight1, objPhraseHighlight2);
        assertEquals(-1, result,
                "o1" + Arrays.toString(o1) + " should be < " + Arrays.toString(o2));
    }

    @ParameterizedTest
    @MethodSource("dataGreaterThan")
    void testBoundedOverlappingUnequalGreaterThanSecondInstances(int @NotNull [] o1, int @NotNull [] o2) {
        var objPhraseHighlight1 =  PhraseHighlight.create(o1[0], o1[1]);
        var objPhraseHighlight2 =  PhraseHighlight.create(o2[0], o2[1]);
        int result = PhraseHighlightComparator.INSTANCE.compare(objPhraseHighlight1, objPhraseHighlight2);
        assertEquals(1, result,
                "o1" + Arrays.toString(o1) + " should be > " + Arrays.toString(o2));
    }

    @Test
    void testMixedBoundedNonOverlappingInstances1() {
        var o1 =  PhraseHighlight.createEnder(10);
        var o2 =  PhraseHighlight.create(15, 30);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(-1, result, "o1[,10] should be < o2[15,30]");
    }

    @Test
    void testMixedBoundedNonOverlappingInstances2() {
        var o1 =  PhraseHighlight.createStarter(15);
        var o2 =  PhraseHighlight.create(0, 10);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(1, result, "o1[15,] should be > o2[0,10]");
    }

    @Test
    void testMixedBoundedOverlappingInstances1() {
        var o1 =  PhraseHighlight.createEnder(20);
        var o2 =  PhraseHighlight.create(15, 30);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(-1, result, "o1[,20] should be < o2[15,30]");
    }

    @Test
    void testMixedBoundedOverlappingInstances2() {
        var o1 =  PhraseHighlight.createStarter(20);
        var o2 =  PhraseHighlight.create(15, 30);
        int result = PhraseHighlightComparator.INSTANCE.compare(o1, o2);
        assertEquals(1, result, "o1[20,] should be > o2[15,30]");
    }
    private static Stream<Arguments> dataLessThan() {
        return Stream.of(
                Arguments.of(new int[]{0, 10}, new int[]{1, 3}),
                Arguments.of(new int[]{0, 10}, new int[]{5, 10})
        );

    }

    private static Stream<Arguments> dataGreaterThan() {
        return Stream.of(
                Arguments.of(new int[]{0, 5}, new int[]{0, 10}),
                Arguments.of(new int[]{5, 15}, new int[]{0, 10})
        );

    }


}
