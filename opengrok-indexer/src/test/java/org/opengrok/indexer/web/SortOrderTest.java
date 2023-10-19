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
 * Copyright (c) 2011, Trond Norbye. All rights reserved. Use is subject to license terms.
 */
package org.opengrok.indexer.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 * @author trond
 */
class SortOrderTest {

    /**
     * Test of values method, of class SortOrder.
     */
    @Test
    void testValues() {
        System.out.println("values");
        SortOrder[] result = SortOrder.values();
        assertEquals(3, result.length);
        assertEquals(SortOrder.LASTMODIFIED, result[0]);
        assertEquals(SortOrder.RELEVANCY, result[1]);
        assertEquals(SortOrder.BY_PATH, result[2]);
    }

    /**
     * Test of valueOf method, of class SortOrder.
     */
    @Test
    void testValueOf() {
        System.out.println("valueOf");
        SortOrder result = SortOrder.valueOf("LASTMODIFIED");
        assertNotNull(result);
        assertEquals("last modified time", result.getDesc());
        result = SortOrder.valueOf("RELEVANCY");
        assertNotNull(result);
        assertEquals("relevance", result.getDesc());
        result = SortOrder.valueOf("BY_PATH");
        assertNotNull(result);
        assertEquals("path", result.getDesc());
    }
}
