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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;

/**
 * Base class for OpenGrok Docker image integration tests.
 * Provides common setup, utilities, and cleanup for Docker container testing.
 */
public abstract class DockerTestBase {

    protected static final String IMAGE_NAME = "opengrok/docker:master";
    protected static final int WEB_PORT = 8080;
    protected static final int REST_PORT = 5000;
    protected static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    protected static GenericContainer<?> container;
    protected static Path tempSrcDir;
    protected static Path tempDataDir;
    protected static OkHttpClient httpClient;

    @BeforeAll
    static void setupContainer() throws IOException {
        // Create temporary directories for source and data volumes
        tempSrcDir = Files.createTempDirectory("opengrok-test-src-");
        tempDataDir = Files.createTempDirectory("opengrok-test-data-");

        // Create a simple test source file
        Files.writeString(tempSrcDir.resolve("test.java"),
            "public class Test {\n    public static void main(String[] args) {}\n}\n");

        // Initialize HTTP client
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .build();

        // Start container with volume mounts
        container = new GenericContainer<>(DockerImageName.parse(IMAGE_NAME))
                .withFileSystemBind(tempSrcDir.toString(), "/opengrok/src")
                .withFileSystemBind(tempDataDir.toString(), "/opengrok/data")
                .withExposedPorts(WEB_PORT, REST_PORT)
                .waitingFor(Wait.forLogMessage(".*Server startup in.*", 1)
                        .withStartupTimeout(STARTUP_TIMEOUT));

        container.start();
    }

    @AfterAll
    static void teardownContainer() throws IOException {
        if (container != null) {
            container.stop();
        }

        // Cleanup temporary directories
        if (tempSrcDir != null && Files.exists(tempSrcDir)) {
            try (var stream = Files.walk(tempSrcDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }

        if (tempDataDir != null && Files.exists(tempDataDir)) {
            try (var stream = Files.walk(tempDataDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    /**
     * Execute a command in the running container.
     *
     * @param command the command to execute
     * @return the command output
     */
    protected static String execInContainer(String... command) {
        try {
            var result = container.execInContainer(command);
            return result.getStdout();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command in container", e);
        }
    }

    /**
     * Execute an HTTP GET request.
     *
     * @param url the URL to request
     * @return the HTTP response
     * @throws IOException if the request fails
     */
    protected static Response httpGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        return httpClient.newCall(request).execute();
    }

    /**
     * Get the URL for the web interface.
     *
     * @return the web interface URL
     */
    protected static String getWebUrl() {
        return String.format("http://%s:%d/",
                container.getHost(),
                container.getMappedPort(WEB_PORT));
    }

    /**
     * Get the URL for the REST API.
     *
     * @return the REST API URL
     */
    protected static String getRestApiUrl() {
        return String.format("http://%s:%d/",
                container.getHost(),
                container.getMappedPort(REST_PORT));
    }

    /**
     * Get container logs.
     *
     * @return the container logs
     */
    protected static String getContainerLogs() {
        return container.getLogs();
    }

    /**
     * Check if a file exists in the container.
     *
     * @param path the file path to check
     * @return true if the file exists
     */
    protected static boolean fileExistsInContainer(String path) {
        try {
            var result = container.execInContainer("test", "-e", path);
            return result.getExitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a directory is writable in the container.
     *
     * @param path the directory path to check
     * @return true if the directory is writable
     */
    protected static boolean isWritableInContainer(String path) {
        try {
            var result = container.execInContainer("test", "-w", path);
            return result.getExitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get file ownership in the container.
     *
     * @param path the file path
     * @return the owner:group string
     */
    protected static String getFileOwnership(String path) {
        try {
            var result = container.execInContainer("stat", "-c", "%U:%G", path);
            if (result.getExitCode() == 0) {
                return result.getStdout().trim();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "unknown";
    }
}
