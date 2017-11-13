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
 * Copyright (c) 2017, cfraire@me.com.
 */

package org.opensolaris.opengrok.util;

import static org.junit.Assert.assertEquals;

/**
 * Represents a container for custom test assertion methods
 */
public class CustomAssertions {
    /**
     * non-public so as to be just a static container class
     */
    protected CustomAssertions() {
    }

    /**
     * Asserts the specified lines arrays have equal contents.
     * @param messagePrefix a message prefixed to line-specific or length-
     * specific errors
     * @param expecteds the expected content of lines
     * @param actuals the actual content of lines
     */
    public static void assertLinesEqual(String messagePrefix,
        String expecteds[], String actuals[]) {

        for (int i = 0; i < expecteds.length && i < actuals.length; i++) {
            if (!expecteds[i].equals(actuals[i])) {
                System.out.print("- ");
                System.out.println(expecteds[i]);
                System.out.print("+ ");
                System.out.println(actuals[i]);
            }
            assertEquals(messagePrefix + ":line " + (i + 1), expecteds[i],
                actuals[i]);
        }

        assertEquals(messagePrefix + ":number of lines", expecteds.length,
            actuals.length);
    }
}
