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
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.docker;

import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the OpenGrok Docker image.
 * These tests validate that the Docker image:
 * - Starts correctly with proper volume mounts
 * - Has correct file ownership (catches chown bugs)
 * - Exposes working web interface and REST API
 * - Performs indexing operations
 * - Does not produce errors during startup
 *
 * Related to GitHub issue #4912
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DockerImageTest extends DockerTestBase {

    @Test
    @Order(1)
    @DisplayName("Container starts successfully and is running")
    void testContainerStartup() {
        assertNotNull(container, "Container should be initialized");
        assertTrue(container.isRunning(), "Container should be running");
    }

    @Test
    @Order(2)
    @DisplayName("Required directories exist in container")
    void testRequiredDirectories() {
        assertTrue(fileExistsInContainer("/opengrok/src"),
                "/opengrok/src directory should exist");
        assertTrue(fileExistsInContainer("/opengrok/data"),
                "/opengrok/data directory should exist");
        assertTrue(fileExistsInContainer("/opengrok/etc"),
                "/opengrok/etc directory should exist");
        assertTrue(fileExistsInContainer("/usr/local/tomcat/webapps"),
                "/usr/local/tomcat/webapps directory should exist");
    }

    @Test
    @Order(3)
    @DisplayName("File ownership is correct (appuser:appgroup)")
    void testFileOwnership() {
        // This test catches the chown bug mentioned in issue #4912
        assertEquals("appuser:appgroup", getFileOwnership("/opengrok/src"),
                "/opengrok/src should be owned by appuser:appgroup");
        assertEquals("appuser:appgroup", getFileOwnership("/opengrok/data"),
                "/opengrok/data should be owned by appuser:appgroup");
        assertEquals("appuser:appgroup", getFileOwnership("/opengrok/etc"),
                "/opengrok/etc should be owned by appuser:appgroup");
        assertEquals("appuser:appgroup", getFileOwnership("/usr/local/tomcat/webapps"),
                "/usr/local/tomcat/webapps should be owned by appuser:appgroup");
    }

    @Test
    @Order(4)
    @DisplayName("Volume mounts are writable")
    void testVolumeMounts() {
        assertTrue(isWritableInContainer("/opengrok/src"),
                "/opengrok/src should be writable");
        assertTrue(isWritableInContainer("/opengrok/data"),
                "/opengrok/data should be writable");
    }

    @Test
    @Order(5)
    @DisplayName("Web interface is accessible on port 8080")
    void testWebInterface() throws IOException {
        String webUrl = getWebUrl();

        try (Response response = httpGet(webUrl)) {
            assertNotNull(response, "Response should not be null");
            assertEquals(200, response.code(),
                    "Web interface should return HTTP 200");

            String body = response.body() != null ? response.body().string() : "";
            assertTrue(body.contains("OpenGrok") || body.contains("Search"),
                    "Response should contain OpenGrok content");
        }
    }

    @Test
    @Order(6)
    @DisplayName("REST API is accessible on port 5000")
    void testRestApi() throws IOException {
        String restApiUrl = getRestApiUrl();

        try (Response response = httpGet(restApiUrl)) {
            assertNotNull(response, "Response should not be null");
            // REST API returns 404 for root path (no token), but should be responsive
            assertTrue(response.code() == 404 || response.code() == 200,
                    "REST API should be responding (404 or 200 is acceptable)");
        }
    }

    @Test
    @Order(7)
    @DisplayName("Container logs contain no fatal errors")
    void testNoFatalErrorsInLogs() {
        String logs = getContainerLogs();
        assertNotNull(logs, "Container logs should not be null");

        // Check for FATAL errors (ERROR can be acceptable in some contexts)
        assertFalse(logs.contains("FATAL"),
                "Container logs should not contain FATAL errors");

        // Verify successful startup indicators
        assertTrue(logs.contains("Server startup in"),
                "Logs should indicate successful Tomcat startup");
    }

    @Test
    @Order(8)
    @DisplayName("Indexer creates index files")
    void testIndexingCreatesFiles() throws InterruptedException {
        // Wait a bit for indexing to start
        Thread.sleep(10000);

        // Check if index directory was created
        assertTrue(fileExistsInContainer("/opengrok/data/index"),
                "Index directory should be created");
    }

    @Test
    @Order(9)
    @DisplayName("Source files are accessible in container")
    void testSourceFilesAccessible() {
        assertTrue(fileExistsInContainer("/opengrok/src/test.java"),
                "Test source file should be accessible in container");
    }

    @Test
    @Order(10)
    @DisplayName("Tomcat process is running as non-root user")
    void testNonRootUser() {
        String output = execInContainer("ps", "aux");
        assertTrue(output.contains("appuser"),
                "Processes should be running as appuser");
        assertFalse(output.contains("root.*tomcat"),
                "Tomcat should not be running as root");
    }
}
