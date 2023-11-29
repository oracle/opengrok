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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a container for sanitizing methods for avoiding classifications as
 * taint bugs.
 */
public class Laundromat {

    private static final String ESC_N_R_T_F = "[\\n\\r\\t\\f]";
    private static final String ESG_N_R_T_F_1_N = ESC_N_R_T_F + "+";

    /**
     * Sanitize {@code value} where it will be used in subsequent OpenGrok
     * (non-logging) processing.
     * @return {@code null} if null or else {@code value} with "pattern-breaking
     * characters" (tabs, CR, LF, FF) replaced as underscores (one for one)
     */
    public static String launderInput(String value) {
        return replaceAll(value, ESC_N_R_T_F, "_");
    }

    /**
     * Sanitize {@code value} where it will be used in a Lucene query.
     * @return {@code null} if null or else {@code value} with "pattern-breaking
     * characters" (tabs, CR, LF, FF) replaced as spaces. Contiguous matches are
     * replaced with one space.
     */
    public static String launderQuery(String value) {
        return replaceAll(value, ESG_N_R_T_F_1_N, " ");
    }

    /**
     * Sanitize {@code value} where it will be used in a log message only.
     * @return {@code null} if null or else {@code value} with "pattern-breaking
     * characters" tabs, CR, LF, and FF replaced as {@code "<TAB>"},
     * {@code "<CR>"}, {@code "<LF>"}, and {@code "<FF>"} resp.
     */
    public static String launderLog(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\n", "<LF>").
                replace("\r", "<CR>").
                replace("\t", "<TAB>").
                replace("\f", "<FF>");
    }

    /**
     * Sanitize {@code map} where it will be used in a log message only.
     * @return {@code null} if null or else {@code map} with keys and values
     * sanitized with {@link #launderLog(String)}. If the sanitizing causes key
     * collisions, the colliding keys' values are combined.
     */
    public static Map<String, String[]> launderLog(Map<String, String[]> map) {

        return Optional.ofNullable(map)
                .stream()
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        entry -> launderLog(entry.getKey()),
                        entry -> launderLogArray(entry.getValue()),
                        Laundromat::mergeLogArrays
                ));
    }

    private static String[] launderLogArray(String[] values){
        return Optional.ofNullable(values)
                .stream().flatMap(Arrays::stream)
                .map(Laundromat::launderLog)
                .toArray(String[]::new);
    }

    private static String[] mergeLogArrays(String[] safeValues1, String[] safeValues2) {
        return Stream.concat(Arrays.stream(safeValues1), Arrays.stream(safeValues2))
                .toArray(String[]::new);

    }

    private static String replaceAll(String value, String regex, String replacement) {
        return Optional.ofNullable(value)
                .map(val -> val.replaceAll(regex, replacement))
                .orElse(null);
    }

    /* private to enforce static */
    private Laundromat() {
    }
}
