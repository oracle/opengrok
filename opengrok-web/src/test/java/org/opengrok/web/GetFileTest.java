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
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.DummyHttpServletRequest;
import org.opengrok.indexer.web.Prefix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetFileTest {
    private static Path sourceRoot;

    private static Path sourceFile;
    private static final String fileContent = "int main();";

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @BeforeAll
    public static void setUpClass() throws IOException {
        sourceRoot = Files.createTempDirectory("tmpDirPrefix");
        sourceFile = Files.createFile(Path.of(sourceRoot.toString(), "foo.c"));
        Files.writeString(sourceFile, fileContent, StandardCharsets.UTF_8);
        env.setSourceRoot(sourceRoot.toString());
    }

    @AfterAll
    public static void tearDownClass() throws IOException {
        IOUtils.removeRecursive(sourceRoot);
    }

    @Test
    void testGetFileNotFound() throws IOException {
        GetFile getFile = new GetFile();
        final String relativePath = "/project/nonexistent.c";
        HttpServletRequest request = new DummyHttpServletRequest() {
            @Override
            public String getPathInfo() {
                return relativePath;
            }
        };
        assertFalse(Path.of(env.getSourceRootPath(), relativePath).toFile().exists());
        HttpServletResponse response = mock(HttpServletResponse.class);
        getFile.service(request, response);
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void testGetFileRedirect() throws Exception {
        GetFile getFile = new GetFile();
        final String relativePath = "/project";
        Path dir = Path.of(env.getSourceRootPath(), relativePath);
        Files.createDirectory(dir);
        final String contextPath = "ctx";
        final String prefix = Prefix.XREF_P.toString();
        HttpServletRequest request = new DummyHttpServletRequest() {
            @Override
            public String getPathInfo() {
                return "";
            }

            @Override
            public String getRequestURI() {
                return "foo";
            }

            @Override
            public String getServletPath() {
                return prefix;
            }

            @Override
            public String getContextPath() {
                return contextPath;
            }
        };
        assertTrue(Path.of(env.getSourceRootPath(), relativePath).toFile().isDirectory());
        HttpServletResponse response = mock(HttpServletResponse.class);
        getFile.service(request, response);
        verify(response).sendRedirect(contextPath + prefix + "/");
    }

    @Test
    void testGetFileWrite() throws Exception {
        GetFile getFileOrig = new GetFile();
        ServletConfig config = mock(ServletConfig.class);
        getFileOrig.init(config);
        GetFile getFile = spy(getFileOrig);
        when(config.getServletContext()).thenReturn(mock(ServletContext.class));
        when(getFile.getServletContext().getMimeType(anyString())).thenReturn("text/css");
        final String relativePath = env.getPathRelativeToSourceRoot(sourceFile.toFile());
        HttpServletRequest request = new DummyHttpServletRequest() {
            @Override
            public String getPathInfo() {
                return relativePath;
            }

            @Override
            public String getServletPath() {
                return "download";
            }

            @Override
            public long getDateHeader(String s) {
                return 1;
            }
        };
        assertTrue(Path.of(env.getSourceRootPath(), relativePath).toFile().exists());
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        getFile.service(request, response);
        verify(outputStream).write(ArgumentMatchers.isNotNull(), eq(0), eq(fileContent.length()));
        verify(outputStream).close();
    }
}
