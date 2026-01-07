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
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.authorization;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test behavior of AuthorizationFramework {@code reload()} w.r.t. HTTP sessions.
 *
 * @author Vladimir Kotal
 */
class AuthorizationFrameworkReloadTest {

    private final File pluginDirectory;
    volatile boolean runThread;

    AuthorizationFrameworkReloadTest() throws URISyntaxException {
        URL url = getClass().getResource("/authorization/plugins/testplugins.jar");
        assertNotNull(url);
        pluginDirectory = Paths.get(url.toURI()).toFile().getParentFile();
    }

    /**
     * After {@code reload()} the session attributes should be invalidated.
     * It is assumed that invalidation of HttpSession objects means that all
     * the attributes will be unset.
     */
    @Test
    void testReloadSimple() {
        DummyHttpServletRequest req = new DummyHttpServletRequest();
        AuthorizationFramework framework = new AuthorizationFramework(pluginDirectory.getPath());
        framework.reload();

        // Ensure the framework was setup correctly.
        assertNotNull(framework.getPluginDirectory());
        assertEquals(pluginDirectory, framework.getPluginDirectory());

        // Create pre-requisite objects - mainly the HTTP session with attribute.
        Project p = new Project("project" + Math.random());
        HttpSession session = req.getSession();
        String attrName = "foo";
        session.setAttribute(attrName, "bar");
        assertNotNull(session.getAttribute(attrName));

        // Reload the framework to increment the plugin generation version.
        framework.reload();
        // Let the framework check the request. This should invalidate the session
        // since the version was incremented. In this test we are not interested
        // in the actual result.
        framework.isAllowed(req, p);
        // Verify that the session no longer has the attribute.
        assertNull(session.getAttribute(attrName));
    }

    /**
     * Sort of a stress test - call isAllowed() and reload() in parallel.
     * This might uncover any snags with locking within AuthorizationFramework.
     */
    @Test
    void testReloadCycle() {
        String projectName = "project" + Math.random();

        // Create authorization stack for single project.
        AuthorizationStack stack = new AuthorizationStack(AuthControlFlag.REQUIRED,
                "stack for project " + projectName);
        assertNotNull(stack);
        stack.add(new AuthorizationPlugin(AuthControlFlag.REQUIRED,
                "opengrok.auth.plugin.FalsePlugin"));
        stack.setForProjects(projectName);
        AuthorizationFramework framework =
                new AuthorizationFramework(pluginDirectory.getPath(), stack);
        framework.reload();

        // Perform simple sanity check before long run is entered. If this fails,
        // it will be waste of time to continue with the test.
        Project p = new Project(projectName);
        DummyHttpServletRequest req = new DummyHttpServletRequest();
        assertFalse(framework.isAllowed(req, p));

        // Create a thread that does reload() every now and then.
        runThread = true;
        final int maxReloadSleep = 10;
        Thread t = new Thread(() -> {
            while (runThread) {
                framework.reload();
                try {
                    Thread.sleep((long) (Math.random() % maxReloadSleep) + 1);
                } catch (InterruptedException ex) {
                }
            }
        });
        t.start();

        // Process number of requests and check that framework decision is consistent.
        for (int i = 0; i < 1000; i++) {
            req = new DummyHttpServletRequest();
            assertFalse(framework.isAllowed(req, p));
            try {
                // Should run more frequently than the thread performing reload().
                Thread.sleep((long) (Math.random() % (maxReloadSleep / 3)) + 1);
            } catch (InterruptedException ex) {
            }
        }

        try {
            // Terminate the thread.
            runThread = false;
            t.join();
        } catch (InterruptedException ex) {
        }

        // Double check that at least one reload() was done.
        long reloads = (long) Metrics.getRegistry().counter("authorization.stack.reload").count();
        assertTrue(reloads > 0);
    }

    @Test
    void testSetLoadClasses() {
        AuthorizationFramework framework = new AuthorizationFramework();
        assertTrue(framework.isLoadClassesEnabled());
        framework.setLoadClasses(false);
        assertFalse(framework.isLoadClassesEnabled());
    }

    @Test
    void testSetLoadJars() {
        AuthorizationFramework framework = new AuthorizationFramework();
        assertTrue(framework.isLoadJarsEnabled());
        framework.setLoadJars(false);
        assertFalse(framework.isLoadJarsEnabled());
    }

    @Test
    void testReloadWithNullPluginDirectory() {
        AuthorizationFramework framework = new AuthorizationFramework(null);
        assertNull(framework.getPluginDirectory());
        framework.reload();
    }

    @Test
    void testReloadWithPluginDirectoryNotDirectory() throws IOException {
        Path tmpFile = Files.createTempFile("pluginFakeDirectory", "");
        assertFalse(tmpFile.toFile().isDirectory());
        AuthorizationFramework framework = new AuthorizationFramework(tmpFile.toString());
        assertNotNull(framework.getPluginDirectory());
        framework.reload();
        Files.delete(tmpFile);
    }
}
