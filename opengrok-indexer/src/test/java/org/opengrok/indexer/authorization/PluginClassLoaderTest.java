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
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.authorization;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.framework.PluginClassLoader;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginClassLoaderTest {

    private final File pluginDirectory;

    public PluginClassLoaderTest() throws URISyntaxException {
        pluginDirectory = Paths.get(getClass().getResource("/authorization/plugins/testplugins.jar").toURI()).toFile().getParentFile();
        assertTrue(pluginDirectory.isDirectory());
    }

    @Test
    public void testProhibitedPackages() {
        PluginClassLoader instance = new PluginClassLoader(null);

        try {
            instance.loadClass("java.lang.plugin.MyPlugin");
            Assert.fail("Should produce SecurityException");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
        }

        try {
            instance.loadClass("javax.servlet.HttpServletRequest");
            Assert.fail("Should produce SecurityException");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
        }

        try {
            instance.loadClass("org.w3c.plugin.MyPlugin");
            Assert.fail("Should produce SecurityException");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
        }

        try {
            instance.loadClass("org.xml.plugin.MyPlugin");
            Assert.fail("Should produce SecurityException");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
        }

        try {
            instance.loadClass("org.omg.plugin.MyPlugin");
            Assert.fail("Should produce SecurityException");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
        }

        try {
            instance.loadClass("sun.org.plugin.MyPlugin");
            Assert.fail("Should produce SecurityException");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
        }
    }

    @Test
    public void testProhibitedNames() {
        PluginClassLoader instance = new PluginClassLoader(null);

        try {
            instance.loadClass("org.opengrok.indexer.configuration.Group");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
            Assert.fail("Should not produce SecurityException");
        } catch (Throwable e) {
        }

        try {
            instance.loadClass("org.opengrok.indexer.configuration.Project");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
            Assert.fail("Should not produce SecurityException");
        } catch (Throwable e) {
        }

        try {
            instance.loadClass("org.opengrok.indexer.authorization.IAuthorizationPlugin");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (SecurityException ex) {
            Assert.fail("Should not produce SecurityException");
        } catch (Throwable e) {
        }

        try {
            instance.loadClass("org.opengrok.indexer.configuration.RuntimeEnvironment");
            Assert.fail("Should produce SecurityException");
        } catch (ClassNotFoundException ex) {
            Assert.fail("Should not produce ClassNotFoundException");
        } catch (Throwable ex) {
        }
    }

    @Test
    public void testNonExistingPlugin() {
        PluginClassLoader instance = new PluginClassLoader(pluginDirectory);

        loadClass(instance, "org.sample.plugin.NoPlugin", true);
    }

    @Test
    public void testFalsePlugin() {
        PluginClassLoader instance = new PluginClassLoader(pluginDirectory);

        Class<?> clazz = loadClass(instance, "opengrok.auth.plugin.FalsePlugin");

        IAuthorizationPlugin plugin = getNewInstance(clazz);

        Group g = new Group("group1");
        Project p = new Project("project1");

        assertFalse(plugin.isAllowed(new DummyHttpServletRequest(), g));
        assertFalse(plugin.isAllowed(new DummyHttpServletRequest(), p));
    }

    @Test
    public void testTruePlugin() {
        PluginClassLoader instance = new PluginClassLoader(pluginDirectory);

        Class<?> clazz = loadClass(instance, "opengrok.auth.plugin.TruePlugin");

        IAuthorizationPlugin plugin = getNewInstance(clazz);

        Group g = new Group("group1");
        Project p = new Project("project1");

        assertTrue(plugin.isAllowed(new DummyHttpServletRequest(), g));
        assertTrue(plugin.isAllowed(new DummyHttpServletRequest(), p));
    }

    private IAuthorizationPlugin getNewInstance(Class<?> c) {
        IAuthorizationPlugin plugin = null;
        try {
            plugin = (IAuthorizationPlugin) c.getDeclaredConstructor().newInstance();
        } catch (InstantiationException ex) {
            Assert.fail("Should not produce InstantiationException");
        } catch (IllegalAccessException ex) {
            Assert.fail("Should not produce IllegalAccessException");
        } catch (Exception ex) {
            Assert.fail("Should not produce any exception");
        }
        return plugin;
    }

    private Class<?> loadClass(PluginClassLoader loader, String name) {
        return loadClass(loader, name, false);
    }

    private Class<?> loadClass(PluginClassLoader loader, String name, boolean shouldFail) {
        Class<?> clazz = null;
        try {
            clazz = loader.loadClass(name);
            if (shouldFail) {
                Assert.fail("Should produce some exception");
            }
        } catch (ClassNotFoundException ex) {
            if (!shouldFail) {
                Assert.fail(String.format("Should not produce ClassNotFoundException: %s", ex.getLocalizedMessage()));
            }
        } catch (SecurityException ex) {
            if (!shouldFail) {
                Assert.fail(String.format("Should not produce SecurityException: %s", ex.getLocalizedMessage()));
            }
        } catch (Exception ex) {
            if (!shouldFail) {
                Assert.fail(String.format("Should not produce any exception: %s", ex.getLocalizedMessage()));
            }
        }
        return clazz;
    }

}
