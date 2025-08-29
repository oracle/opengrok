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
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaundromatTest {

    private static final String TEST_CONTENT = "\n\r\f\tContent\r\nConclusion\f\t";
    private static final String TEST_CONTENT_LOG_LAUNDRY =
            "<LF><CR><FF><TAB>Content<CR><LF>Conclusion<FF><TAB>";

    @Test
    void launderInput() {
        String laundry = Laundromat.launderInput(TEST_CONTENT);
        assertEquals("____Content__Conclusion__", laundry);
    }

    @Test
    void launderQuery() {
        String laundry = Laundromat.launderQuery(TEST_CONTENT);
        assertEquals(" Content Conclusion ", laundry);
    }

    @Test
    void launderLog() {
        String laundry = Laundromat.launderLog(TEST_CONTENT);
        assertEquals(TEST_CONTENT_LOG_LAUNDRY, laundry);
    }

    private static Stream<Pair<String, String>> getParamsForTestLaunderServerName() {
        return Stream.of(Pair.of("foo.example.com", Laundromat.launderServerName("--foo.example\n.com?=")),
                Pair.of("[2001:db8::1]:8080", Laundromat.launderServerName("[2001:db8::1]:8080")));
    }

    @ParameterizedTest
    @MethodSource("getParamsForTestLaunderServerName")
    void testLaunderServerName(Pair<String, String> param) {
        assertEquals(param.getLeft(), param.getRight());
    }

    private static Stream<Pair<String, String>> getParamsForTestLaunderUriPath() {
        return Stream.of(Pair.of("foo", Laundromat.launderUriPath("../../../foo")),
                Pair.of("/foo/bar", Laundromat.launderUriPath("/foo/../../bar")),
                Pair.of("/foo/bar..", Laundromat.launderUriPath("/foo/bar..")),
                Pair.of("/foo/bar", Laundromat.launderUriPath("/foo//bar")),
                Pair.of("..foo/bar", Laundromat.launderUriPath("..foo/bar")),
                Pair.of("/foo/bar.txt", Laundromat.launderUriPath("/foo/bar.txt")),
                Pair.of("/foo/bar_.txt", Laundromat.launderUriPath("/foo/bar\u0000.txt")));
    }

    @ParameterizedTest
    @MethodSource("getParamsForTestLaunderUriPath")
    void testLaunderUriPath(Pair<String, String> param) {
        assertEquals(param.getLeft(), param.getRight());
    }

    @Test
    void launderLogMap() {
        HashMap<String, String[]> testMap = new HashMap<>();
        testMap.put("a", null);
        testMap.put("b", new String[]{TEST_CONTENT});
        testMap.put(TEST_CONTENT, new String[]{"c", "d"});
        // for merging two non-null collisions
        testMap.put("e\rf", new String[]{"c", "d"});
        testMap.put("e<CR>f", new String[]{"g", "h"});
        // for merging LHS null on collisions
        testMap.put("\fi\n", null);
        testMap.put("<FF>i\n", new String[]{"j"});
        testMap.put("<FF>i<LF>", new String[]{"k"});
        // for merging RHS null on collisions
        testMap.put("l\t\r", null);
        testMap.put("l<TAB>\r", null);
        testMap.put("l\t<CR>", null);

        Map<String, String[]> laundry = Laundromat.launderLog(testMap);

        Map<String, String[]> expected = new HashMap<>();
        expected.put("a", new String[0]);
        expected.put("b", new String[]{TEST_CONTENT_LOG_LAUNDRY});
        expected.put(TEST_CONTENT_LOG_LAUNDRY, new String[]{"c", "d"});
        expected.put("e<CR>f", new String[]{"g", "h", "c", "d"});
        expected.put("<FF>i<LF>", new String[]{"k", "j"});
        expected.put("l<TAB><CR>", new String[0]);

        assertEquals(hashedValues(expected), hashedValues(laundry), "maps″ should be equal");
    }

    private Map<String, Integer> hashedValues(Map<String, String[]> map) {
        HashMap<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, String[]> entry : map.entrySet()) {
            result.put(entry.getKey(), Arrays.hashCode(entry.getValue()));
        }
        return result;
    }
}
