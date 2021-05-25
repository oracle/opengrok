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
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the {@code StringUtils} class.
 *
 * @author Vladimir Kotal
 */
public class StringUtilsTest {

    @Test
    public void testValues() {
        int i;
        long[] values = {
            0, 100, 1000, 1500, 64000, 124531, 3651782, 86400000, 86434349,
            1075634299, 86480001, 86400001
        };
        String[] expected = {
            "0 ms", "100 ms", "1.0 seconds", "1.500 seconds", "0:01:04",
            "0:02:04", "1:00:51", "1 day", "1 day",
            "12 days 10:47:14", "1 day 00:01:20", "1 day"
        };

        for (i = 0; i < values.length; i++) {
            assertEquals(expected[i], StringUtils.getReadableTime(values[i]));
        }
    }

    @Test
    public void testNthIndexOf() {
        Object[][] tests = new Object[][]{
            {"", "", -1},
            {"", "", 0},
            {"", "", 1},
            {"", "", 2},
            {"foo", "foo", 0},
            {"foo", "foo", 1},
            {"foo", "foo", 2},
            {"foo", "foo", 3},
            {"foo", "f", 0},
            {"foo", "f", 1},
            {"foo", "f", 2},
            {"foo", "f", 3},
            {"foo", "o", 0},
            {"foo", "o", 1},
            {"foo", "o", 2},
            {"foo", "o", 3},
            {"This is an example string", "a", 2},
            {"This is an example string", "a", 3},
            {"This is an example string", "i", 1},
            {"This is an example string", "i", 2},
            {"This is an example string", "i", 3},
            {"This is an example string", "is", 1},
            {"This is an example string", "is", 2},
            {"aabbccddaabbccdd", "a", 1},
            {"aabbccddaabbccdd", "a", 2},
            {"aabbccddaabbccdd", "a", 3},
            {"aabbccddaabbccdd", "a", 4},
            {"aabbccddaabbccdd", "cd", 1},
            {"aabbccddaabbccdd", "cd", 2},
            {"aabbccddaabbccdd", "ccdd", 1},
            {"aabbccddaabbccdd", "ccdd", 2}};

        int[] indices = new int[]{
            -1, -1, 0, -1,
            -1, 0, -1, -1,
            -1, 0, -1, -1,
            -1, 1, 2, -1,
            13, -1,
            2, 5, 22,
            2, 5,
            0, 1, 8, 9,
            5, 13,
            4, 12
        };

        assertEquals(tests.length, indices.length);

        for (int i = 0; i < tests.length; i++) {
            int index = StringUtils.nthIndexOf((String) tests[i][0], (String) tests[i][1], (Integer) tests[i][2]);
            assertEquals(index, indices[i],
                    String.format("%d-th occurrence of \"%s\" in \"%s\" should start at %d but started at %d",
                    tests[i][2], tests[i][1], tests[i][0], indices[i], index));
        }
    }

    @Test
    public void uriShouldNotCountAnyPushback() {
        String uri = "http://www.example.com";
        int n = StringUtils.countURIEndingPushback(uri);
        assertEquals(0, n, uri + " pushback");
    }

    @Test
    public void uriAtSentenceEndShouldCountPushback() {
        String uri = "http://www.example.com.";
        int n = StringUtils.countURIEndingPushback(uri);
        assertEquals(1, n, uri + " pushback");
    }

    @Test
    public void uriEmptyShouldNotCountAnyPushback() {
        String uri = "";
        int n = StringUtils.countURIEndingPushback(uri);
        assertEquals(0, n, "empty pushback");
    }

    @Test
    public void shouldMatchNonescapedApostrophe() {
        // Copy-and-paste the following so Netbeans does the escaping:
        // value: \'1-2-3\''
        final String value = "\\'1-2-3\\''";
        int i = StringUtils.patindexOf(value, StringUtils.APOS_NO_BSESC);
        assertEquals(9, i, "unquoted apostrophe");
    }

    @Test
    public void shouldMatchApostropheAfterEvenEscapes() {
        // Copy-and-paste the following so Netbeans does the escaping:
        // value: \\'
        final String value = "\\\\'";
        int i = StringUtils.patindexOf(value, StringUtils.APOS_NO_BSESC);
        assertEquals(2, i, "unquoted apostrophe after backslashes");
    }

    @Test
    public void shouldNotMatchApostropheAfterOddEscapes() {
        // Copy-and-paste the following so Netbeans does the escaping:
        // value: \\\'
        final String value = "\\\\\\'";
        int i = StringUtils.patindexOf(value, StringUtils.APOS_NO_BSESC);
        assertEquals(-1, i, "quoted apostrophe after backslashes");
    }

    @Test
    public void shouldMatchInitialApostrophe() {
        final String value = "'";
        int i = StringUtils.patindexOf(value, StringUtils.APOS_NO_BSESC);
        assertEquals(0, i, "initial apostrophe");
    }
}
