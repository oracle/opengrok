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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import org.junit.Test;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.EftarFileReader;
import org.opengrok.web.api.v1.RestApp;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SystemControllerTest extends OGKJerseyTest {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Override
    protected Application configure() {
        return new RestApp();
    }

    /**
     * This method tests include files reload by testing it for one specific file out of the whole set.
     * @throws IOException
     */
    @Test
    public void testIncludeReload() throws IOException {
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
        Response r = target("system")
                .path("includes").path("reload")
                .request().put(Entity.text(""));
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), r.getStatus());

        // Check that the content was reloaded.
        String after = env.getIncludeFiles().getFooterIncludeFileContent(false);
        assertNotEquals(before, after);
        assertEquals(content, after.trim());

        // Cleanup
        IOUtils.removeRecursive(includeRootPath);
    }

    @Test
    public void testDtagsEftarReload() throws IOException {
        // The output file will be located in a directory under data root so create it first.
        Path dataRoot = Files.createTempDirectory("api_dtags_test");
        env.setDataRoot(dataRoot.toString());
        Paths.get(dataRoot.toString(), "index").toFile().mkdir();

        // Create path descriptions string.
        StringBuilder sb = new StringBuilder();
        String[][] descriptions = {
                {"/path1", "foo foo"},
                {"/path2", "bar bar"}
        };

        for (String[] description : descriptions) {
            sb.append(description[0]);
            sb.append("\t");
            sb.append(description[1]);
            sb.append("\n");
        }
        String input = sb.toString();

        // Reload the contents via API call.
        Response r = target("system")
                .path("pathdesc")
                .request().post(Entity.text(input));
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), r.getStatus());

        // Check
        Path eftarPath = env.getDtagsEftarPath();
        assertTrue(eftarPath.toFile().exists());
        try (EftarFileReader er = new EftarFileReader(eftarPath.toString())) {
            for (String[] description : descriptions) {
                assertEquals(description[1], er.get(description[0]));
            }
        }

        // Cleanup
        IOUtils.removeRecursive(dataRoot);
    }
}
