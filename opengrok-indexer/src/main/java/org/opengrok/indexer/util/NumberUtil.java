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
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

public class NumberUtil {
    /**
     * Parses the specified {@code value} without throwing a checked exception.
     */
    public static Long tryParseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses the specified {@code value} without throwing a checked exception,
     * returning a default 0.
     */
    public static long tryParseLongPrimitive(String value) {
        Long parsed = tryParseLong(value);
        if (parsed == null) {
            return 0;
        }
        return parsed;
    }

    /* private to enforce static */
    private NumberUtil() {
    }
}
