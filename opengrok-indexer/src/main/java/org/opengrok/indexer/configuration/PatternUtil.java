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
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PatternUtil {
    private PatternUtil() {
        // private to enforce static
    }

    /**
     * A check if a pattern contains at least one pair of parentheses meaning
     * that there is at least one capture group. This group must not be empty.
     */
    static final String PATTERN_SINGLE_GROUP = ".*\\([^\\)]+\\).*";
    /**
     * Error string for invalid patterns without a single group. This is passed
     * as a first argument to the constructor of PatternSyntaxException and in
     * the output it is followed by the invalid pattern.
     *
     * @see PatternSyntaxException
     * @see #PATTERN_SINGLE_GROUP
     */
    static final String PATTERN_MUST_CONTAIN_GROUP = "The pattern must contain at least one non-empty group -";

    /**
     * Check and compile the bug pattern.
     *
     * @param pattern the new pattern
     * @throws PatternSyntaxException when the pattern is not a valid regexp or
     * does not contain at least one capture group and the group does not
     * contain a single character
     */
    public static String compilePattern(String pattern) throws PatternSyntaxException {
        if (!pattern.matches(PATTERN_SINGLE_GROUP)) {
            throw new PatternSyntaxException(PATTERN_MUST_CONTAIN_GROUP, pattern, 0);
        }
        return Pattern.compile(pattern).toString();
    }
}
