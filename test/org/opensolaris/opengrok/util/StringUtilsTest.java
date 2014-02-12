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
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.util;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

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
            1075634299
        };
        String[] expected = {
            "0", "100 ms", "1.0 seconds", "1.500 seconds", "0:01:04",
            "0:02:04", "1:00:51", "1 day", "1 day 34.349 seconds",
            "12 days 10:47:14"
        };
        
        for (i = 0; i < values.length; i++) {
            assertEquals(expected[i], StringUtils.getReadableTime(values[i]));
        }
    }
}
