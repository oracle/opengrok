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
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.Info;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.EftarFileReader;
import org.opengrok.indexer.web.PathDescription;
import org.opengrok.web.api.v1.RestApp;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemControllerTest extends OGKJerseyTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Override
    protected DeploymentContext configureDeployment() {
        return ServletDeploymentContext.forServlet(new ServletContainer(new RestApp())).build();
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
    }

    /**
     * This method tests include files reload by testing it for one specific file out of the whole set.
     * @throws IOException on error
     */
    @Test
    void testIncludeReload() throws IOException {
        // Set new include root directory.
        Path includeRootPath = Files.createTempDirectory("systemControllerTest");
        File includeRoot = includeRootPath.toFile();
        env.setIncludeRoot(includeRoot.getAbsolutePath());
        assertEquals(includeRoot.getCanonicalPath(), env.getIncludeRootPath());

        // Create footer include file.
        File footerFile = new File(includeRoot, Configuration.FOOTER_INCLUDE_FILE);
        String content = "foo";
        try (PrintWriter out = new PrintWriter(footerFile)) {
            out.println(content);
        }

        // Sanity check that getFooterIncludeFileContent() works since the test depends on it.
        String before = env.getIncludeFiles().getFooterIncludeFileContent(false);
        assertEquals(content, before.trim());

        // Modify the contents of the file.
        content = content + "bar";
        try (PrintWriter out = new PrintWriter(footerFile)) {
            out.println(content);
        }

        // Reload the contents via API call.
        try (Response r = target("system")
                .path("includes").path("reload")
                .request().put(Entity.text(""))) {
            assertEquals(Response.Status.NO_CONTENT.getStatusCode(), r.getStatus());
        }

        // Check that the content was reloaded.
        String after = env.getIncludeFiles().getFooterIncludeFileContent(false);
        assertNotEquals(before, after);
        assertEquals(content, after.trim());

        // Cleanup
        IOUtils.removeRecursive(includeRootPath);
    }

    @Test
    void testDtagsEftarReload() throws IOException {
        // The output file will be located in a directory under data root so create it first.
        Path dataRoot = Files.createTempDirectory("api_dtags_test");
        env.setDataRoot(dataRoot.toString());
        assertTrue(Paths.get(dataRoot.toString(), "index").toFile().mkdir());

        // Create path descriptions string.
        PathDescription[] descriptions = {
                new PathDescription("/path1", "foo foo"),
                new PathDescription("/path2", "bar bar")
        };

        // Reload the contents via API call.
        try (Response r = target("system")
                .path("pathdesc")
                .request().post(Entity.json(descriptions))) {
            assertEquals(Response.Status.NO_CONTENT.getStatusCode(), r.getStatus());
        }

        // Check
        Path eftarPath = env.getDtagsEftarPath();
        assertTrue(eftarPath.toFile().exists());
        try (EftarFileReader er = new EftarFileReader(eftarPath.toString())) {
            for (PathDescription description : descriptions) {
                assertEquals(description.getDescription(), er.get(description.getPath()));
            }
        }

        // Cleanup
        IOUtils.removeRecursive(dataRoot);
    }

    @Test
    void testIndexTime() throws IOException, ParseException {
        Path dataRoot = Files.createTempDirectory("indexTimetest");
        env.setDataRoot(dataRoot.toString());
        Path indexTimeFile = dataRoot.resolve("timestamp");
        Files.createFile(indexTimeFile);
        assertTrue(Files.exists(indexTimeFile));
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd_hh:mm:ss+ZZ");
        Date date = f.parse("2021-02-16_11:18:01+UTC");
        Files.setLastModifiedTime(indexTimeFile, FileTime.fromMillis(date.getTime()));

        Response r = target("system")
                .path(SystemController.INDEX_TIME)
                .request().get();
        String result = r.readEntity(String.class);

        assertEquals("\"2021-02-16T11:18:01.000+00:00\"", result);

        // Cleanup
        IOUtils.removeRecursive(dataRoot);
    }

    @Test
    void testVersion() {
        Response r = target("system")
                .path("version")
                .request().get();
        String result = r.readEntity(String.class);

        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        assertEquals(String.format("%s (%s)", Info.getVersion(), Info.getRevision()), result);
    }

    @Test
    void testPing() {
        Response r = target("system")
                .path("ping")
                .request().get();
        String result = r.readEntity(String.class);

        assertEquals(Response.Status.OK.getStatusCode(), r.getStatus());
        assertTrue(result.isEmpty());
    }
}
