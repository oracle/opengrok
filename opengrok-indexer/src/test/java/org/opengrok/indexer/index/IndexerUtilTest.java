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
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexerUtilTest {

    private IndexerUtil indexerUtil;

    @BeforeEach
    void setUp() {
        indexerUtil = new IndexerUtil(10, 30, null);
    }

    @Test
    void testGetWebAppHeadersNotNull() {
        MultivaluedMap<String, Object> headers = indexerUtil.getWebAppHeaders();

        assertNotNull(headers);
    }

    @Test
    void testGetWebAppHeadersWithoutBearerToken() {
        MultivaluedMap<String, Object> headers = indexerUtil.getWebAppHeaders();

        assertFalse(headers.containsKey(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testGetWebAppHeadersWithBearerToken() {
        IndexerUtil indexerUtil = new IndexerUtil(10, 30, "test-token");

        MultivaluedMap<String, Object> headers = indexerUtil.getWebAppHeaders();

        assertEquals("Bearer test-token",
                headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void testEnableProjectsInvalidUrl() {
        assertThrows(ProcessingException.class, () ->
                indexerUtil.enableProjects("http://non-existent.server.com:123"));
    }

    @Test
    void testGetProjectsInvalidUrl() {
        assertThrows(ProcessingException.class, () ->
                indexerUtil.getProjects("http://non-existent.server.com:123"));
    }
}