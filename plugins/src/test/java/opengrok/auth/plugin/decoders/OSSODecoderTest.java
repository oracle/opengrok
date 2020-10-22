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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.decoders;

import static opengrok.auth.plugin.decoders.OSSOHeaderDecoder.OSSO_COOKIE_TIMESTAMP_HEADER;
import static opengrok.auth.plugin.decoders.OSSOHeaderDecoder.OSSO_SUBSCRIBER_DN_HEADER;
import static opengrok.auth.plugin.decoders.OSSOHeaderDecoder.OSSO_SUBSCRIBER_HEADER;
import static opengrok.auth.plugin.decoders.OSSOHeaderDecoder.OSSO_TIMEOUT_EXCEEDED_HEADER;
import static opengrok.auth.plugin.decoders.OSSOHeaderDecoder.OSSO_USER_DN_HEADER;
import static opengrok.auth.plugin.decoders.OSSOHeaderDecoder.OSSO_USER_GUID_HEADER;

import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.util.DummyHttpServletRequestUser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test OSSO header decoder.
 * @author Krystof Tulinger
 */
public class OSSODecoderTest {

    DummyHttpServletRequestUser dummyRequest;
    OSSOHeaderDecoder decoder = new OSSOHeaderDecoder();

    @Before
    public void setUp() {
        dummyRequest = new DummyHttpServletRequestUser();
        dummyRequest.setHeader(OSSO_COOKIE_TIMESTAMP_HEADER, "5761172f");
        dummyRequest.setHeader(OSSO_TIMEOUT_EXCEEDED_HEADER, "");
        dummyRequest.setHeader(OSSO_SUBSCRIBER_DN_HEADER, "");
        dummyRequest.setHeader(OSSO_SUBSCRIBER_HEADER, "");
        dummyRequest.setHeader(OSSO_USER_DN_HEADER, "007");
        dummyRequest.setHeader(OSSO_USER_GUID_HEADER, "123456");
    }

    /**
     * Test of fromRequest method, of class User.
     */
    public void testAll() {
        dummyRequest.setHeader(OSSO_COOKIE_TIMESTAMP_HEADER, "5761172f");
        dummyRequest.setHeader(OSSO_TIMEOUT_EXCEEDED_HEADER, "false");
        dummyRequest.setHeader(OSSO_SUBSCRIBER_DN_HEADER, "dn=example.com");
        dummyRequest.setHeader(OSSO_SUBSCRIBER_HEADER, "example.com");
        dummyRequest.setHeader(OSSO_USER_DN_HEADER, "dn=specific.dn");
        dummyRequest.setHeader(OSSO_USER_GUID_HEADER, "123456");

        User result = decoder.fromRequest(dummyRequest);

        Assert.assertNotNull(result);
        Assert.assertEquals("dn=specific.dn", result.getUsername());
        Assert.assertEquals("123456", result.getId());
        Assert.assertFalse(result.getTimeouted());
        Assert.assertEquals(Long.parseLong("1465980719000"), result.getCookieTimestamp().getTime());
        Assert.assertFalse(result.isTimeouted());
    }

    /**
     * Test of getUserId method, of class User.
     */
    @Test
    public void testGetUserId() {
        String[] tests = {
                "123456",
                "sd45gfgf5sd4g5ffd54g",
                "ě5 1g56ew1tč6516re5g1g65d1g65d"
        };

        for (String test : tests) {
            dummyRequest.setHeader(OSSO_USER_GUID_HEADER, test);
            User result = decoder.fromRequest(dummyRequest);
            Assert.assertNotNull(result);
            Assert.assertEquals(test, result.getId());
        }
    }

    /**
     * Test of getUserDn method, of class User.
     */
    @Test
    public void testGetUserDn() {
        String[] tests = {
                "123456",
                "sd45gfgf5sd4g5ffd54g",
                "ě5 1g56ew1tč6516re5g1g65d1g65d"
        };

        for (String test : tests) {
            dummyRequest.setHeader(OSSO_USER_DN_HEADER, test);
            User result = decoder.fromRequest(dummyRequest);
            Assert.assertNotNull(result);
            Assert.assertEquals(test, result.getUsername());
        }
    }

    /**
     * Test of getCookieTimestamp method, of class User.
     */
    @Test
    public void testGetCookieTimestamp() {
        String[] tests = {"123456", "5761172f", "58d137be"};
        long[] expected = {1193046000L, 1465980719000L, 1490106302000L};

        for (int i = 0; i < tests.length; i++) {
            dummyRequest.setHeader(OSSO_COOKIE_TIMESTAMP_HEADER, tests[i]);
            User result = decoder.fromRequest(dummyRequest);
            Assert.assertNotNull(result);
            Assert.assertEquals(expected[i], result.getCookieTimestamp().getTime());
        }
    }

    /**
     * Test of getCookieTimestamp method, of class User.
     */
    @Test
    public void testInvalidGetCookieTimestamp() {
        String[] tests = {
                "sd45gfgf5sd4g5ffd54g",
                "ě5 1g56ew1tč6516re5g1g65d1g65d",
                "",
                "ffffx" // not a hex number
        };

        for (String test : tests) {
            User u;
            dummyRequest.setHeader(OSSO_COOKIE_TIMESTAMP_HEADER, test);
            Assert.assertNotNull(u = decoder.fromRequest(dummyRequest));
            Assert.assertNull(u.getCookieTimestamp());
        }
    }

    /**
     * Test of getTimeoutExceeded method, of class User.
     */
    @Test
    public void testGetTimeouted() {
        String[] tests = {"false", "true", "FALSE", "TRUE", "abcd"};
        boolean[] expected = {false, true, false, true, false};

        for (int i = 0; i < tests.length; i++) {
            dummyRequest.setHeader(OSSO_TIMEOUT_EXCEEDED_HEADER, tests[i]);
            User result = decoder.fromRequest(dummyRequest);
            if (expected[i]) {
                Assert.assertNull(result);
            } else {
                Assert.assertNotNull(result);
            }
        }
    }
}
