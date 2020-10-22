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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

/**
 * Represents a container for regex-related utility methods.
 */
public class RegexUtils {

    /** Private to enforce singleton. */
    private RegexUtils() {
    }

    /**
     * Gets a zero-width expression intended to follow a character match that
     * asserts that the character either does not follow a backslash escape or
     * follows an even number¹ of backslash escapes.
     * <p>
     * ¹"even number" is limited to 2,4,6 because Java look-behind is not
     * variable length but instead must have a definite upper bound in the
     * regex definition.
     * @return a defined expression:
     * <pre>
     * {@code
     * ((?<=^.)|(?<=[^\\].)|(?<=^(\\\\){1,3}.)|(?<=[^\\](\\\\){1,3}.))
     * }
     * </pre>
     * (Edit above and paste below [in NetBeans] for easy String escaping.)
     */
    public static String getNotFollowingEscapePattern() {
        return "((?<=^.)|(?<=[^\\\\].)|(?<=^(\\\\\\\\){1,3}.)|(?<=[^\\\\](\\\\\\\\){1,3}.))";
    }
}
