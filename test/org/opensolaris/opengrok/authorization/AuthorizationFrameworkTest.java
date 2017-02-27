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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.authorization;

import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.DummyHttpServletRequest;

import static org.junit.Assert.assertEquals;

public class AuthorizationFrameworkTest {

    private static String pluginDirectory;

    /**
     * Test of isAllowed method.
     *
     * 2 test plugins loaded
     */
    @Test
    public void test2Plugins() {
        AuthorizationFramework instance = getInstance();
        invokeAddPlugin(createSample2Plugin());
        invokeAddPlugin(createSamplePlugin());

        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    /**
     * Test of isAllowed method.
     *
     * Test plugin loaded
     */
    @Test
    public void testPlugin() {
        AuthorizationFramework instance = getInstance();
        invokeAddPlugin(createSamplePlugin());

        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));

        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedProject()));

        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));

        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    /**
     * Test of getInstance method.
     */
    @Test
    public void testGetInstance() {
        AuthorizationFramework result = getInstance();
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof AuthorizationFramework);
    }

    /**
     * Test of isAllowed method.
     *
     * No plugins loaded.
     */
    @Test
    public void testNoPlugins() {
        assertEquals(getInstance().isAllowed(createRequest(), createAllowedProject()), true);
        assertEquals(getInstance().isAllowed(createRequest(), createAllowedGroup()), true);
    }

    @BeforeClass
    public static void tearUpClass() {
        pluginDirectory = RuntimeEnvironment.getInstance().getConfiguration().getPluginDirectory();
        RuntimeEnvironment.getInstance().getConfiguration().setPluginDirectory(null);
    }

    @AfterClass
    public static void tearDownClass() {
        RuntimeEnvironment.getInstance().getConfiguration().setPluginDirectory(pluginDirectory);
    }

    private void invokeRemoveAll() {
        try {
            Method method = AuthorizationFramework.class.getDeclaredMethod("removeAll");
            method.setAccessible(true);
            method.invoke(AuthorizationFramework.getInstance());
        } catch (Exception ex) {
            Assert.fail("invokeRemoveAll should not throw an exception");
        }
    }

    private void invokeAddPlugin(IAuthorizationPlugin plugin) {
        try {
            Method method = AuthorizationFramework.class.getDeclaredMethod("addPlugin", new Class[]{IAuthorizationPlugin.class});
            method.setAccessible(true);
            method.invoke(AuthorizationFramework.getInstance(), new Object[]{plugin});
        } catch (Exception ex) {
            Assert.fail("invokeAddPlugin should not throw an exception");
        }
    }

    private AuthorizationFramework getInstance() {
        invokeRemoveAll();
        return AuthorizationFramework.getInstance();
    }

    private Project createAllowedProject() {
        Project p = new Project();
        p.setName("allowed" + "_" + "project" + Math.random());
        return p;
    }

    private Project createUnallowedProject() {
        Project p = new Project();
        p.setName("not_allowed" + "_" + "project" + Math.random());
        return p;
    }

    private Group createAllowedGroup() {
        Group g = new Group();
        g.setName("allowed" + "_" + "group_" + Math.random());
        return g;
    }

    private Group createUnallowedGroup() {
        Group g = new Group();
        g.setName("not_allowed" + "_" + "group_" + Math.random());
        return g;
    }

    private HttpServletRequest createRequest() {
        return new DummyHttpServletRequest();
    }

    private IAuthorizationPlugin createSamplePlugin() {
        return new TestPlugin() {
            @Override
            public boolean isAllowed(HttpServletRequest request, Project project) {
                return project.getName().startsWith("allowed");
            }

            @Override
            public boolean isAllowed(HttpServletRequest request, Group group) {
                return group.getName().startsWith("allowed");
            }
        };
    }

    private IAuthorizationPlugin createSample2Plugin() {
        return new TestPlugin() {
            @Override
            public boolean isAllowed(HttpServletRequest request, Project project) {
                return project.getName().startsWith("not_allowed");
            }

            @Override
            public boolean isAllowed(HttpServletRequest request, Group group) {
                return group.getName().startsWith("not_allowed");
            }
        };
    }
}
