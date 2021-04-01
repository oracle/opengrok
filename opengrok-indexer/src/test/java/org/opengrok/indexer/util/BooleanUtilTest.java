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

package org.opengrok.indexer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BooleanUtilTest {

    // Test written by Diffblue Cover.
    @Test
    public void isBooleanInputNotNullOutputFalse() {
        final String value = "3";
        final boolean actual = BooleanUtil.isBoolean(value);
        assertFalse(actual);
    }

    // Test written by Diffblue Cover.
    @Test
    public void isBooleanInputNotNullOutputTrue() {
        final String value = "1";
        final boolean actual = BooleanUtil.isBoolean(value);
        assertTrue(actual);
    }

    // Test written by Diffblue Cover.
    @Test
    public void isBooleanInputNotNullOutputTrue2() {
        final String value = "faLSe";
        final boolean actual = BooleanUtil.isBoolean(value);
        assertTrue(actual);
    }

    // Test written by Diffblue Cover.
    @Test
    public void toIntegerInputFalseOutputZero() {
        final boolean b = false;
        final int actual = BooleanUtil.toInteger(b);
        assertEquals(0, actual);
    }

    // Test written by Diffblue Cover.
    @Test
    public void toIntegerInputTrueOutputPositive() {
        final boolean b = true;
        final int actual = BooleanUtil.toInteger(b);
        assertEquals(1, actual);
    }
}
