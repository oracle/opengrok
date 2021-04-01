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

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.framework.PluginClassLoader;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginClassLoaderTest {

    private final File pluginDirectory;

    public PluginClassLoaderTest() throws URISyntaxException {
        pluginDirectory = Paths.get(getClass().getResource("/authorization/plugins/testplugins.jar").toURI()).toFile().getParentFile();
        assertTrue(pluginDirectory.isDirectory());
    }

    @Test
    public void testProhibitedPackages() {
        PluginClassLoader instance = new PluginClassLoader(null);

        assertThrows(SecurityException.class, () -> instance.loadClass("java.lang.plugin.MyPlugin"));
        assertThrows(SecurityException.class, () -> instance.loadClass("javax.servlet.HttpServletRequest"));
        assertThrows(SecurityException.class, () -> instance.loadClass("org.w3c.plugin.MyPlugin"));
        assertThrows(SecurityException.class, () -> instance.loadClass("org.xml.plugin.MyPlugin"));
        assertThrows(SecurityException.class, () -> instance.loadClass("org.omg.plugin.MyPlugin"));
        assertThrows(SecurityException.class, () -> instance.loadClass("sun.org.plugin.MyPlugin"));
    }

    @Test
    public void testProhibitedNames() throws ClassNotFoundException {
        PluginClassLoader instance = new PluginClassLoader(null);

        instance.loadClass("org.opengrok.indexer.configuration.Group");
        instance.loadClass("org.opengrok.indexer.configuration.Project");
        instance.loadClass("org.opengrok.indexer.authorization.IAuthorizationPlugin");
        instance.loadClass("org.opengrok.indexer.configuration.RuntimeEnvironment");
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
        try {
            return (IAuthorizationPlugin) c.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?> loadClass(PluginClassLoader loader, String name) {
        return loadClass(loader, name, false);
    }

    private Class<?> loadClass(PluginClassLoader loader, String name, boolean shouldFail) {
        if (shouldFail) {
            assertThrows(Exception.class, () -> loader.loadClass(name));
            return null;
        } else {
            try {
                return loader.loadClass(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
