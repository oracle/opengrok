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
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.decoders;

import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.util.DummyHttpServletRequestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UserPrincipalDecoderTest {
    DummyHttpServletRequestUser dummyRequest;
    UserPrincipalDecoder decoder = new UserPrincipalDecoder();

    @BeforeEach
    public void setUp() {
        dummyRequest = new DummyHttpServletRequestUser();
    }

    @Test
    public void testHttpBasicDecoding() {
        dummyRequest.setHeader("authorization", "Basic Zm9vOmJhcg==");

        User result = decoder.fromRequest(dummyRequest);

        assertNotNull(result);
        assertEquals("foo", result.getUsername());
        assertNull(result.getId());
        assertFalse(result.isTimeouted());
    }

    @Test
    public void testMissingHeader() {
        assertNull(decoder.fromRequest(new DummyHttpServletRequestUser()));
    }
}

