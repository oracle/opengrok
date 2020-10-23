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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import javax.servlet.http.HttpServletRequest;

import opengrok.auth.plugin.decoders.OSSOHeaderDecoder;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.util.DummyHttpServletRequestUser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

/**
 *
 * @author Krystof Tulinger
 */
public class UserPluginTest {

    private UserPlugin plugin;

    @Before
    public void setUp() {
        plugin = new UserPlugin(new OSSOHeaderDecoder());
    }

    @Test
    public void testNoUser() {
        Assert.assertFalse(plugin.isAllowed(new DummyHttpServletRequestUser(), new Group()));
        Assert.assertFalse(plugin.isAllowed(new DummyHttpServletRequestUser(), new Project()));
        Assert.assertFalse(plugin.isAllowed(new DummyHttpServletRequestUser(), createGroup("some group")));
        Assert.assertFalse(plugin.isAllowed(new DummyHttpServletRequestUser(), createProject("some project")));
    }

    @Test
    public void testUser() {
        HttpServletRequest req;
        Assert.assertTrue(plugin.isAllowed(req = createRequest("007"), new Group()));
        Assert.assertEquals("007", ((User) req.getAttribute(UserPlugin.REQUEST_ATTR)).getUsername());
        Assert.assertTrue(plugin.isAllowed(req = createRequest("008"), new Project()));
        Assert.assertEquals("008", ((User) req.getAttribute(UserPlugin.REQUEST_ATTR)).getUsername());
        Assert.assertTrue(plugin.isAllowed(req = createRequest("009"), createGroup("some group")));
        Assert.assertEquals("009", ((User) req.getAttribute(UserPlugin.REQUEST_ATTR)).getUsername());
        Assert.assertTrue(plugin.isAllowed(req = createRequest("00A"), createProject("some project")));
        Assert.assertEquals("00A", ((User) req.getAttribute(UserPlugin.REQUEST_ATTR)).getUsername());
    }

    @Test
    public void testTimeoutedUser() {
        HttpServletRequest req;
        Assert.assertFalse(plugin.isAllowed(req = createRequest("007", true), new Group()));
        Assert.assertNull(req.getAttribute(UserPlugin.REQUEST_ATTR));
        Assert.assertFalse(plugin.isAllowed(req = createRequest("008", true), new Project()));
        Assert.assertNull(req.getAttribute(UserPlugin.REQUEST_ATTR));
        Assert.assertFalse(plugin.isAllowed(req = createRequest("009", true), createGroup("some group")));
        Assert.assertNull(req.getAttribute(UserPlugin.REQUEST_ATTR));
        Assert.assertFalse(plugin.isAllowed(req = createRequest("00A", true), createProject("some project")));
        Assert.assertNull(req.getAttribute(UserPlugin.REQUEST_ATTR));
    }

    protected HttpServletRequest createRequest(String email) {
        return createRequest(email, false);
    }

    protected HttpServletRequest createRequest(String email, Boolean timeout) {
        return new DummyHttpServletRequestUser() {
            {
                setHeader("osso-user-dn", email);
                setHeader("osso-user-guid", "100");
                setHeader("osso-idle-timeout-exceeded", Boolean.toString(timeout));
            }
        };
    }

    protected Group createGroup(String name) {
        Group g = new Group();
        g.setName(name);
        return g;
    }

    protected Project createProject(String name) {
        Project g = new Project();
        g.setName(name);
        return g;
    }
}
