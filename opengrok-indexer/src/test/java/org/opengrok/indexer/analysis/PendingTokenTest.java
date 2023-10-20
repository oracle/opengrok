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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Represents a container for tests of {@link PendingToken}.
 */
class PendingTokenTest {

    @Test
    void testEquals1() {
        PendingToken instance = new PendingToken("", 0, 0);
        boolean result = instance.equals(instance);
        assertTrue(result, "PendingToken instance equals itself");
    }

    @Test
    void testEquals2() {
        PendingToken instance1 = new PendingToken("a", 0, 1);
        PendingToken instance2 = new PendingToken("a", 0, 1);
        boolean result = instance1.equals(instance2);
        assertTrue(result, "PendingToken instance equivalence false");
    }

    @ParameterizedTest
    @MethodSource
    void testNotEquals(PendingToken instance2) {
        PendingToken instance1 = new PendingToken("", 0, 0);
        boolean result = instance1.equals(instance2);
        assertFalse(result, "PendingToken equals() only 2 immutables match");
    }
    static Stream<PendingToken> testNotEquals() {
        return Stream.of(new PendingToken("", 0, 1),
                new PendingToken("", 1, 0),
                new PendingToken("a", 0, 0));
    }

    @Test
    void testSameHashCodes() {
        PendingToken instance1 = new PendingToken("a", 0, 1);
        PendingToken instance2 = new PendingToken("a", 0, 1);
        assertEquals(instance1.hashCode(), instance2.hashCode(), "PendingToken instance HashCode differs");
    }

    @Test
    void testDifferentHashCodes1() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("", 0, 1); // nonsense but ok
        assertNotEquals(instance1.hashCode(), instance2.hashCode(), "PendingToken hashCode() only 2 immutables match");
    }

    @Test
    void testDifferentHashCodes2() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("", 1, 0); // nonsense but ok
        assertNotEquals(instance1.hashCode(), instance2.hashCode(), "PendingToken hashCode() only 2 immutables match");
    }

    @Test
    void testDifferentHashCodes3() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("a", 0, 0); // nonsense but ok
        assertNotEquals(instance1.hashCode(), instance2.hashCode(), "PendingToken hashCode() only 2 immutables match");
    }

    @Test
    void testToString() {
        PendingToken instance = new PendingToken("abc", 0, 4);
        String expResult = "PendingToken{abc<<< start=0,end=4}";
        String result = instance.toString();
        assertEquals(expResult, result, "PendingToken toString()");
    }
}
