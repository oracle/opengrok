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
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.search.context;

import java.util.Locale;

/**
 * Base class for matching a line against terms
 *
 */
public abstract class LineMatcher {
    public static String tagBegin, tagEnd;
    public static final int NOT_MATCHED = 0;
    public static final int MATCHED = 1;
    public static final int WAIT = 2;    

    /**
     * Tells whether the matching should be done in a case insensitive manner.
     */
    private final boolean caseInsensitive;

    /**
     * Create a {@code LineMatcher} instance.
     *
     * @param caseInsensitive if {@code true}, matching should not consider
     * case differences significant
     */
    LineMatcher(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    /**
     * Check if two strings are equal. If this is a case insensitive matcher,
     * the check will return true if the only difference between the strings
     * is difference in case.
     */
    boolean equal(String s1, String s2) {
        return compareStrings(s1, s2) == 0;
    }

    /**
     * Compare two strings and return -1, 0 or 1 if the first string is
     * lexicographically smaller than, equal to or greater than the second
     * string. If this is a case insensitive matcher, case differences will
     * be ignored.
     */
    int compareStrings(String s1, String s2) {
        if (s1 == null) {
            return s2 == null ? 0 : -1;
        } else if (caseInsensitive) {
            return s1.compareToIgnoreCase(s2);
        } else {
            return s1.compareTo(s2);
        }
    }

    /**
     * Normalize a string token for comparison with other string tokens. That
     * is, convert to lower case if this is a case insensitive matcher.
     * Otherwise, return the string itself.
     */
    String normalizeString(String s) {
        if (s == null) {
            return null;
        } else if (caseInsensitive) {
            return s.toLowerCase(Locale.getDefault());
        } else {
            return s;
        }
    }

    public abstract int match(String line);
}
