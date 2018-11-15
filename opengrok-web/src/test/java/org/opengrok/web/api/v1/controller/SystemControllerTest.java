package org.opengrok.web.api.v1.controller;


import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SystemControllerTest extends JerseyTest {

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
                { "/path1", "foo foo" },
                { "/path2", "bar bar" }
        };

        for (int i = 0; i < descriptions.length; i++) {
            sb.append(descriptions[i][0]);
            sb.append("\t");
            sb.append(descriptions[i][1]);
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
        EftarFileReader er = new EftarFileReader(eftarPath.toString());
        for (int i = 0; i < descriptions.length; i++) {
            assertEquals(descriptions[i][1], er.get(descriptions[i][0]));
        }

        // Cleanup
        IOUtils.removeRecursive(dataRoot);
    }
}
