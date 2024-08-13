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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GetFileTest {
    private static Path sourceRoot;

    @BeforeAll
    public static void setUpClass() throws IOException {
        sourceRoot = Files.createTempDirectory("tmpDirPrefix");
        // TODO: create a file
        RuntimeEnvironment.getInstance().setSourceRoot(sourceRoot.toString());
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
        assertFalse(Path.of(RuntimeEnvironment.getInstance().getSourceRootPath(), relativePath).toFile().exists());
        HttpServletResponse response = mock(HttpServletResponse.class);
        getFile.service(request, response);
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
