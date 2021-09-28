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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Aleksandr Kirillov <alexkirillovsamara@gmail.com>.
 */
package org.opengrok.indexer.configuration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test include file functionality for web application.
 *
 * @author Vladimir Kotal
 */
public class IncludeFilesTest {
    static Path includeRoot;
    static final String CONTENT_1 = "foo";
    static final String CONTENT_2 = "bar";
    static RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @BeforeAll
    public static void setUpClass() throws IOException {
        includeRoot = Files.createTempDirectory("include_root");
        env.setIncludeRoot(includeRoot.toString());
    }

    private void writeStringToFile(Path file, String str) throws IOException {
        Files.writeString(file, str, Charset.defaultCharset());
    }

    @Test
    public void testGetHeaderIncludeFileContent() throws IOException {
        Path file = includeRoot.resolve(Configuration.HEADER_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1, env.includeFiles.getHeaderIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2, env.includeFiles.getHeaderIncludeFileContent(true));
    }

    @Test
    public void testGetBodyIncludeFileContent() throws IOException {
        Path file = includeRoot.resolve(Configuration.BODY_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1, env.includeFiles.getBodyIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2, env.includeFiles.getBodyIncludeFileContent(true));
    }

    @Test
    public void testGetFooterIncludeFileContent() throws IOException {
        Path file = includeRoot.resolve(Configuration.FOOTER_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1, env.includeFiles.getFooterIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2, env.includeFiles.getFooterIncludeFileContent(true));
    }

    @Test
    public void testGetForbiddenIncludeFileContent() throws IOException {
        Path file = includeRoot.resolve(Configuration.E_FORBIDDEN_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1, env.includeFiles.getForbiddenIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2, env.includeFiles.getForbiddenIncludeFileContent(true));
    }

    @Test
    public void testGetHttpHeaderIncludeFileContent() throws IOException {
        Path file = includeRoot.resolve(Configuration.HTTP_HEADER_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1, env.includeFiles.getHttpHeaderIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2, env.includeFiles.getHttpHeaderIncludeFileContent(true));
    }
}
