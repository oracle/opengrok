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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.search.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the WildCardMatcher class.
 */
class WildCardMatcherTest {

    /**
     * Test of match method.
     */
    @Test
    void testMatch() {
        WildCardMatcher m = new WildCardMatcher("wild?ard", true); // bug #15644
        assertEquals(LineMatcher.MATCHED, m.match("wildcard"));
        assertEquals(LineMatcher.MATCHED, m.match("wildward"));
        assertEquals(LineMatcher.MATCHED, m.match("wilddard"));
        assertEquals(LineMatcher.MATCHED, m.match("wild?ard"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("wildard"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("wildcarde"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("awildcard"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("wildddard"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("mildcard"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("wildc?rd"));

        m = new WildCardMatcher("wild*ard", true);
        assertEquals(LineMatcher.MATCHED, m.match("wildcard"));
        assertEquals(LineMatcher.MATCHED, m.match("wildward"));
        assertEquals(LineMatcher.MATCHED, m.match("wilddard"));
        assertEquals(LineMatcher.MATCHED, m.match("wildard"));
        assertEquals(LineMatcher.MATCHED, m.match("wildxyzard"));
        assertEquals(LineMatcher.MATCHED, m.match("wildxyzard"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("wild"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("ard"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("wildcat"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("wildcarda"));
        assertEquals(LineMatcher.NOT_MATCHED, m.match("mildcard"));
    }

}
