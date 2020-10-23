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
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.util;

import java.util.Date;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author ktulinge
 */
public class TimestampTest {

    /**
     * Test of valid timestamp cookies and their decoded values.
     */
    @Test
    public void testDecodeTimestamp() {
        String[] tests = {
            "123456",
            "5761172f",
            "abcdef09",
            "58cfe588"
        };

        long[] expected = {
            1193046000L,
            1465980719000L,
            2882400009000L,
            1490019720000L
        };

        for (int i = 0; i < tests.length; i++) {
            Assert.assertEquals(expected[i], Timestamp.decodeTimeCookie(tests[i]).getTime());
        }
    }

    /**
     * Test of invalid timestamp cookies.
     */
    @Test
    public void testInvalidDecodeTimestamp() {
        String[] tests = {
            "sd45gfgf5sd4g5ffd54g",
            "ě5 1g56ew1tč6516re5g1g65d1g65d",
            "abcegkjkjsdlkjg",
            ""
        };

        for (String test : tests) {
            try {
                Timestamp.decodeTimeCookie(test).getTime();
                Assert.fail("Decoding should throw an exception - invalid format");
            } catch (Exception e) {
            }
        }
    }

    /**
     * Test of encoded cookies.
     */
    @Test
    public void testEncodeTimestamp() {
        Date[] tests = {
            new Date(Long.parseLong("1193046000")),
            new Date(Long.parseLong("1465980719000")),
            new Date(Long.parseLong("2882400009000")),
            new Date(1490019720000L), // 2017-03-20 14:22:00
        };

        String[] expected = {
            "123456",
            "5761172f",
            "abcdef09",
            "58cfe588"
        };

        for (int i = 0; i < tests.length; i++) {
            Assert.assertEquals(expected[i], Timestamp.encodeTimeCookie(tests[i]));
        }
    }
}
