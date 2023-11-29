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
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.decoders;

import jakarta.servlet.http.HttpServletRequest;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.util.DummyHttpServletRequestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPrincipalDecoderTest {
    DummyHttpServletRequestUser dummyRequest;
    UserPrincipalDecoder decoder = new UserPrincipalDecoder();

    @BeforeEach
    void setUp() {
        dummyRequest = new DummyHttpServletRequestUser();
    }

    @Test
    void testHttpBasicDecoding() {
        dummyRequest.setHeader("authorization", "Basic Zm9vOmJhcg==");

        User result = decoder.fromRequest(dummyRequest);

        assertNotNull(result);
        assertEquals("foo", result.getUsername());
        assertNull(result.getId());
        assertFalse(result.isTimeouted());
    }

    private static Collection<Principal> invalidPrincipals() {
         return Arrays.asList(() -> null, () -> "");
    }

    @ParameterizedTest
    @MethodSource("invalidPrincipals")
    void testInvalidUsername(Principal principal) {
        HttpServletRequest mockRequest = mock(DummyHttpServletRequestUser.class);
        when(mockRequest.getUserPrincipal()).thenReturn(principal);
        User result = decoder.fromRequest(mockRequest);
        assertNull(result);
    }

    @Test
    void testMissingHeader() {
        assertNull(decoder.fromRequest(new DummyHttpServletRequestUser()));
    }
}

