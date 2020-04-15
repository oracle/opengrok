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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.web;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a container for sanitizing methods for avoiding classifications as
 * taint bugs.
 */
public class LaunderUtil {

    /**
     * Sanitize {@code value} where it will be used in subsequent OpenGrok
     * (non-logging) processing.
     * @return {@code null} if null or else {@code value} with "pattern-breaking
     * characters" (tabs, CR, LF, FF) replaced as underscores (one for one)
     */
    public static String userInput(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\n\\r\\t\\f]", "_");
    }

    /**
     * Sanitize {@code query} where it will be used in a Lucene query.
     * @return {@code null} if null or else {@code query} with "pattern-breaking
     * characters" (tabs, CR, LF, FF) replaced as spaces. Contiguous matches are
     * replaced with one space.
     */
    public static String luceneQuery(String query) {
        if (query == null) {
            return null;
        }
        return query.replaceAll("[\\n\\r\\t\\f]+", " ");
    }

    /**
     * Sanitize {@code value} where it will be used in a log message only.
     * @return {@code null} if null or else {@code value} with "pattern-breaking
     * characters" tabs, CR, LF, and FF replaced as {@code "<TAB>"},
     * {@code "<CR>"}, {@code "<LF>"}, and {@code "<FF>"} resp.
     */
    public static String logging(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\n", "<LF>").
                replaceAll("\\r", "<CR>").
                replaceAll("\\t", "<TAB>").
                replaceAll("\\f", "<FF>");
    }

    /**
     * Sanitize {@code map} where it will be used in a log message only.
     * @return {@code null} if null or else {@code map} with keys and values
     * sanitized with {@link #logging(String)}. If the sanitizing causes key
     * collisions, the colliding keys' values are combined.
     */
    public static Map<String, String[]> logging(Map<String, String[]> map) {
        if (map == null) {
            return null;
        }

        HashMap<String, String[]> safes = new HashMap<>();
        for (Map.Entry<String, String[]> entry : map.entrySet()) {
            String k = logging(entry.getKey());
            String[] safeValues = safes.get(k);
            String[] fullySafe = mergeLogArrays(entry.getValue(), safeValues);
            safes.put(k, fullySafe);
        }
        return safes;
    }

    private static String[] mergeLogArrays(String[] values, String[] safeValues) {
        int n = values.length + (safeValues != null ? safeValues.length : 0);
        String[] result = new String[n];

        int i;
        for (i = 0; i < values.length; ++i) {
            result[i] = logging(values[i]);
        }
        if (safeValues != null) {
            System.arraycopy(safeValues, 0, result, i, safeValues.length);
        }
        return result;
    }

    /* private to enforce static */
    private LaunderUtil() {
    }
}
