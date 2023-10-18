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

import static opengrok.auth.plugin.decoders.MellonHeaderDecoder.MELLON_EMAIL_HEADER;
import static opengrok.auth.plugin.decoders.MellonHeaderDecoder.MELLON_USERNAME_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MellonDecoderTest {
    DummyHttpServletRequestUser dummyRequest;
    MellonHeaderDecoder decoder = new MellonHeaderDecoder();

    @BeforeEach
    void setUp() {
        dummyRequest = new DummyHttpServletRequestUser();
        dummyRequest.setHeader(MELLON_EMAIL_HEADER, "foo@bar.cz");
    }

    @Test
    void testId() {
        User result = decoder.fromRequest(dummyRequest);

        assertNotNull(result);
        assertEquals("foo@bar.cz", result.getId());
        assertNull(result.getUsername());
        assertFalse(result.isTimeouted());
    }

    @Test
    void testMissingHeader() {
        assertNull(decoder.fromRequest(new DummyHttpServletRequestUser()));
    }

    @Test
    void testUsername() {
        dummyRequest.setHeader(MELLON_USERNAME_HEADER, "foo");
        User result = decoder.fromRequest(dummyRequest);

        assertNotNull(result);
        assertEquals("foo", result.getUsername());
        assertFalse(result.isTimeouted());
    }
}
