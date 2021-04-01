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
package org.opengrok.indexer.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Represents a container for tests of {@link PendingToken}.
 */
public class PendingTokenTest {

    @Test
    public void testEquals1() {
        PendingToken instance = new PendingToken("", 0, 0);
        boolean result = instance.equals(instance);
        assertTrue(result, "PendingToken instance equals itself");
    }

    @Test
    public void testEquals2() {
        PendingToken instance1 = new PendingToken("a", 0, 1);
        assertFalse(instance1.nonpos, "PendingToken default nonpos");

        PendingToken instance2 = new PendingToken("a", 0, 1);
        instance2.nonpos = true;
        boolean result = instance1.equals(instance2);
        assertTrue(result, "PendingToken instance equivalence ignores nonpos");
    }

    @Test
    public void testNotEquals1() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("", 0, 1); // nonsense but ok
        boolean result = instance1.equals(instance2);
        assertFalse(result, "PendingToken equals() only 2 immutables match");
    }

    @Test
    public void testNotEquals2() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("", 1, 0); // nonsense but ok
        boolean result = instance1.equals(instance2);
        assertFalse(result, "PendingToken equals() only 2 immutables match");
    }

    @Test
    public void testNotEquals3() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("a", 0, 0); // nonsense but ok
        boolean result = instance1.equals(instance2);
        assertFalse(result, "PendingToken equals() only 2 immutables match");
    }

    @Test
    public void testSameHashCodes() {
        PendingToken instance1 = new PendingToken("a", 0, 1);
        assertFalse(instance1.nonpos, "PendingToken default nonpos");

        PendingToken instance2 = new PendingToken("a", 0, 1);
        instance2.nonpos = true;
        assertEquals(instance1.hashCode(), instance2.hashCode(), "PendingToken instance HashCode ignores nonpos");
    }

    @Test
    public void testDifferentHashCodes1() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("", 0, 1); // nonsense but ok
        assertNotEquals(instance1.hashCode(), instance2.hashCode(), "PendingToken hashCode() only 2 immutables match");
    }

    @Test
    public void testDifferentHashCodes2() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("", 1, 0); // nonsense but ok
        assertNotEquals(instance1.hashCode(), instance2.hashCode(), "PendingToken hashCode() only 2 immutables match");
    }

    @Test
    public void testDifferentHashCodes3() {
        PendingToken instance1 = new PendingToken("", 0, 0);
        PendingToken instance2 = new PendingToken("a", 0, 0); // nonsense but ok
        assertNotEquals(instance1.hashCode(), instance2.hashCode(), "PendingToken hashCode() only 2 immutables match");
    }

    @Test
    public void testToString() {
        PendingToken instance = new PendingToken("abc", 0, 4);
        String expResult = "PendingToken{abc<<< start=0,end=4,nonpos=false}";
        String result = instance.toString();
        assertEquals(expResult, result, "PendingToken toString()");

        instance.nonpos = true;
        expResult = "PendingToken{abc<<< start=0,end=4,nonpos=true}";
        result = instance.toString();
        assertEquals(expResult, result, "PendingToken toString()");
    }
}
