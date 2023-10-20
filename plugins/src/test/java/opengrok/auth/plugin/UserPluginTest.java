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
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import jakarta.servlet.http.HttpServletRequest;
import opengrok.auth.plugin.decoders.OSSOHeaderDecoder;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.util.DummyHttpServletRequestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Krystof Tulinger
 */
class UserPluginTest {

    private UserPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new UserPlugin(new OSSOHeaderDecoder());
    }

    @Test
    void testNoUser() {
        assertFalse(plugin.isAllowed(new DummyHttpServletRequestUser(), new Group()));
        assertFalse(plugin.isAllowed(new DummyHttpServletRequestUser(), new Project()));
        assertFalse(plugin.isAllowed(new DummyHttpServletRequestUser(), createGroup("some group")));
        assertFalse(plugin.isAllowed(new DummyHttpServletRequestUser(), createProject("some project")));
    }

    @Test
    void testUser() {
        HttpServletRequest req;
        assertTrue(plugin.isAllowed(req = createRequest("007"), new Group()));
        assertEquals("007", ((User) req.getAttribute(UserPlugin.REQUEST_ATTR)).getUsername());
        assertTrue(plugin.isAllowed(req = createRequest("008"), new Project()));
        assertEquals("008", ((User) req.getAttribute(UserPlugin.REQUEST_ATTR)).getUsername());
        assertTrue(plugin.isAllowed(req = createRequest("009"), createGroup("some group")));
        assertEquals("009", ((User) req.getAttribute(UserPlugin.REQUEST_ATTR)).getUsername());
        assertTrue(plugin.isAllowed(req = createRequest("00A"), createProject("some project")));
        assertEquals("00A", ((User) req.getAttribute(UserPlugin.REQUEST_ATTR)).getUsername());
    }

    @Test
    void testTimeoutedUser() {
        HttpServletRequest req;
        assertFalse(plugin.isAllowed(req = createRequest("007", true), new Group()));
        assertNull(req.getAttribute(UserPlugin.REQUEST_ATTR));
        assertFalse(plugin.isAllowed(req = createRequest("008", true), new Project()));
        assertNull(req.getAttribute(UserPlugin.REQUEST_ATTR));
        assertFalse(plugin.isAllowed(req = createRequest("009", true), createGroup("some group")));
        assertNull(req.getAttribute(UserPlugin.REQUEST_ATTR));
        assertFalse(plugin.isAllowed(req = createRequest("00A", true), createProject("some project")));
        assertNull(req.getAttribute(UserPlugin.REQUEST_ATTR));
    }

    HttpServletRequest createRequest(String email) {
        return createRequest(email, false);
    }

    HttpServletRequest createRequest(String email, Boolean timeout) {
        return new DummyHttpServletRequestUser() {
            {
                setHeader("osso-user-dn", email);
                setHeader("osso-user-guid", "100");
                setHeader("osso-idle-timeout-exceeded", Boolean.toString(timeout));
            }
        };
    }

    private Group createGroup(String name) {
        Group g = new Group();
        g.setName(name);
        return g;
    }

    private Project createProject(String name) {
        Project g = new Project();
        g.setName(name);
        return g;
    }

    @Test
    void loadTestNegativeNoDecoderParam() {
        Map<String, Object> params = new TreeMap<>();
        params.put("foo", "bar");
        assertThrows(NullPointerException.class, () -> plugin.load(params));
    }

    @Test
    void loadTestNegativeInvalidDecoder() {
        Map<String, Object> params = new TreeMap<>();
        params.put(UserPlugin.DECODER_CLASS_PARAM, "foo");
        assertThrows(RuntimeException.class, () -> plugin.load(params));
    }

    @Test
    void loadTestPositive() {
        Map<String, Object> params = new TreeMap<>();
        params.put(UserPlugin.DECODER_CLASS_PARAM, "opengrok.auth.plugin.decoders.MellonHeaderDecoder");
        assertDoesNotThrow(() -> plugin.load(params));
    }
}
