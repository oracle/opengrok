package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.server.*;
import org.glassfish.jersey.test.*;
import org.junit.*;
import org.mockito.*;
import org.opengrok.indexer.configuration.*;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.util.*;
import org.opengrok.web.api.v1.*;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;

import java.io.*;
import java.nio.file.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SystemControllerTest extends JerseyTest {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Override
    protected Application configure() {
        return new RestApp();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testIncludeReload() throws IOException {
        // Set new include root directory.
        Path includeRootPath = Files.createTempDirectory("systemControllerTest");
        File includeRoot = includeRootPath.toFile();
        env.setIncludeRoot(includeRoot.getAbsolutePath());
        assertEquals(includeRoot.getAbsolutePath(), env.getIncludeRootPath());

        // Create footer include file.
        File footerFile = new File(includeRoot, Configuration.FOOTER_INCLUDE_FILE);
        String content = "foo";
        try (PrintWriter out = new PrintWriter(footerFile)) {
            out.println(content);
        }

        // Sanity check that getFooterIncludeFileContent() works since the test depends on it.
        String before = env.getConfiguration().getFooterIncludeFileContent(false);
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
        String after = env.getConfiguration().getFooterIncludeFileContent(false);
        assertNotEquals(before, after);
        assertEquals(content, after.trim());

        // Cleanup
        IOUtils.removeRecursive(includeRootPath);
    }
}
