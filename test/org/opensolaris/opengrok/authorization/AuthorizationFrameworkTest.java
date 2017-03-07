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

import javax.servlet.http.HttpServletRequest;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.authorization.AuthorizationCheck.AuthorizationRole;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.DummyHttpServletRequest;

import static org.junit.Assert.assertEquals;

public class AuthorizationFrameworkTest {

    private static String pluginDirectory;

    @Test
    public void test2Plugins() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createNotAllowedPrefixPlugin());
        instance.addPlugin(createAllowedPrefixPlugin());

        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    /* TEST ROLE PLUGINS */
    @Test
    public void testRolePlugins() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createNotAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);

        // sufficient is ok
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
        // sufficient fails and required is ok
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    @Test
    public void testRolePlugins1() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createNotAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);

        // sufficient fails and required is ok
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
        // sufficient is ok
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    @Test
    public void testRolePlugin2() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createNotAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);

        // all are sufficient - success
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    @Test
    public void testRolePlugin3() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createNotAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);

        // required fails
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedGroup()));
        // required is ok
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin4() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);

        // same instance is not added twice in the plugins so
        // there is only one plugin set as sufficient thus everything is true
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin5() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);

        // same instance is not added twice in the plugins so
        // there is only one plugin set as required
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
        // required fails
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin6() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUISITE);
        instance.addPlugin(createNotAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);

        // requisite is ok and the other plugin is sufficient
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
        // requisite fails
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin7() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUISITE);
        instance.addPlugin(createNotAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);

        // requisite is ok however required fails
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin8() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createNotAllowedPrefixPlugin(), AuthorizationRole.REQUISITE);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);

        // requisite fails
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    /* TESTING LOAD FAILING PLUGINS */
    @Test
    public void testRolePlugins9() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createLoadFailingPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);
        instance.loadAllPlugins();

        // sufficient fails and required fails as well
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
        // sufficient fails and required is ok
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    @Test
    public void testRolePlugin10() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createLoadFailingPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);
        instance.loadAllPlugins();

        // all are sufficient - success
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    @Test
    public void testRolePlugin11() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createLoadFailingPlugin(), AuthorizationRole.REQUIRED);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);
        instance.loadAllPlugins();

        // required load fails
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin12() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createLoadFailingPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createLoadFailingPlugin(), AuthorizationRole.REQUIRED);
        instance.loadAllPlugins();

        // same instance is not added twice in the plugins
        // and the sufficient was added first therefore all are success
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin13() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createLoadFailingPlugin(), AuthorizationRole.REQUIRED);
        instance.addPlugin(createLoadFailingPlugin(), AuthorizationRole.SUFFICIENT);
        instance.loadAllPlugins();

        // same instance is not added twice in the plugins
        // and this instance fails all the time
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    /* TESTING TEST FAILING PLUGINS */
    @Test
    public void testRolePlugins14() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createTestFailingPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.REQUIRED);
        instance.loadAllPlugins();

        // sufficient is ok
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        // sufficient fails and required fails as well
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
        // sufficient fails and required is ok
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    @Test
    public void testRolePlugin15() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createTestFailingPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);
        instance.loadAllPlugins();

        // all are sufficient - success
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
    }

    @Test
    public void testRolePlugin16() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createTestFailingPlugin(), AuthorizationRole.REQUIRED);
        instance.addPlugin(createAllowedPrefixPlugin(), AuthorizationRole.SUFFICIENT);
        instance.loadAllPlugins();

        // required test fails (exception when group is not allowed)
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertFalse(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin17() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createTestFailingPlugin(), AuthorizationRole.SUFFICIENT);
        instance.addPlugin(createTestFailingPlugin(), AuthorizationRole.REQUIRED);
        instance.loadAllPlugins();

        // same instance is not added twice in the plugins and the plugin stays sufficient
        // therefore all tests are true
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedGroup()));
    }

    @Test
    public void testRolePlugin18() {
        AuthorizationFramework instance = getInstance();
        instance.addPlugin(createTestFailingPlugin(), AuthorizationRole.REQUIRED);
        instance.addPlugin(createTestFailingPlugin(), AuthorizationRole.SUFFICIENT);
        instance.loadAllPlugins();

        // same instance is not added twice in the plugins and this instance allows all projects
        // and throws an exception when the group is unallowed
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedProject()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createAllowedGroup()));
        Assert.assertTrue(instance.isAllowed(createRequest(), createUnallowedProject()));
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

    private AuthorizationFramework getInstance() {
        AuthorizationFramework.getInstance().removeAll();
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

    private IAuthorizationPlugin createAllowedPrefixPlugin() {
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

    private IAuthorizationPlugin createNotAllowedPrefixPlugin() {
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

    private IAuthorizationPlugin createLoadFailingPlugin() {
        return new TestPlugin() {
            @Override
            public void load() {
                throw new NullPointerException("This plugin failed while loading.");
            }

            @Override
            public boolean isAllowed(HttpServletRequest request, Project project) {
                return true;
            }

            @Override
            public boolean isAllowed(HttpServletRequest request, Group group) {
                return true;
            }

        };
    }

    private IAuthorizationPlugin createTestFailingPlugin() {
        return new TestPlugin() {
            @Override
            public boolean isAllowed(HttpServletRequest request, Project project) {
                return true;
            }

            @Override
            public boolean isAllowed(HttpServletRequest request, Group group) {
                if (group.getName().contains("not_allowed")) {
                    throw new NullPointerException("This group is not allowed.");
                }
                return true;
            }
        };
    }
}
