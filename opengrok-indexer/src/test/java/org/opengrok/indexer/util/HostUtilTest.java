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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Basic tests for the {@link HostUtil} class. Ideally, these should use mocking to properly verify
 * the exceptions and code flow. Also, positive test is missing.
 */
public class HostUtilTest {
    @Test
    void testInvalidURI() {
        assertFalse(HostUtil.isReachable("htt://localhost:8080/source/", 1000, null));
    }

    @Test
    void testInvalidHost() {
        assertFalse(HostUtil.isReachable("http://localhosta:8080/source/", 1000, null));
    }

    @Test
    void testInvalidPort() {
        assertFalse(HostUtil.isReachable("http://localhost:zzzz/source/", 1000, null));
    }

    @Test
    void testNotReachableWebApp() {
        assertFalse(HostUtil.isReachable("http://localhost:4444/source/", 1000, null));
    }
}
