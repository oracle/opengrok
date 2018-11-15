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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    static final String LINE_SEP = System.lineSeparator();
    
    @BeforeClass
    public static void setUpClass() throws IOException {
        includeRoot = Files.createTempDirectory("include_root");
        env.setIncludeRoot(includeRoot.toString());
    }
    
    private void writeStringToFile(File file, String str) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(str);
        }
    }
    
    @Test
    public void testGetHeaderIncludeFileContent() throws IOException {
        File file = new File(includeRoot.toFile(), Configuration.HEADER_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1 + LINE_SEP,
                env.includeFiles.getHeaderIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2 + LINE_SEP,
                env.includeFiles.getHeaderIncludeFileContent(true));
    }
    
    @Test
    public void testGetBodyIncludeFileContent() throws IOException {
        File file = new File(includeRoot.toFile(), Configuration.BODY_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1 + LINE_SEP,
                env.includeFiles.getBodyIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2 + LINE_SEP,
                env.includeFiles.getBodyIncludeFileContent(true));
    }
    
    @Test
    public void testGetFooterIncludeFileContent() throws IOException {
        File file = new File(includeRoot.toFile(), Configuration.FOOTER_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1 + LINE_SEP,
                env.includeFiles.getFooterIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2 + LINE_SEP,
                env.includeFiles.getFooterIncludeFileContent(true));
    }
    
    @Test
    public void testGetForbiddenIncludeFileContent() throws IOException {
        File file = new File(includeRoot.toFile(), Configuration.E_FORBIDDEN_INCLUDE_FILE);
        writeStringToFile(file, CONTENT_1);
        assertEquals(CONTENT_1 + LINE_SEP,
                env.includeFiles.getForbiddenIncludeFileContent(false));
        writeStringToFile(file, CONTENT_2);
        assertEquals(CONTENT_2 + LINE_SEP,
                env.includeFiles.getForbiddenIncludeFileContent(true));
    }
}
