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
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IOUtilsTest {
    private static Path rootPath;

    private static final List<String> FILE_LIST = List.of("foo.txt", "bar.c");

    @BeforeAll
    public static void setUpClass() throws IOException {
        rootPath = Files.createTempDirectory("IOUtilsTest");
        for (String fileName : FILE_LIST) {
            Files.createFile(rootPath.resolve(fileName));
        }
    }

    @AfterAll
    public static void tearDownClass() throws IOException {
        IOUtils.removeRecursive(rootPath);
    }

    @Test
    void testListFilesRecursivelyNullSuffix() {
        var fileList = IOUtils.listFilesRecursively(rootPath.toFile(), null);
        assertNotNull(fileList);
        assertEquals(FILE_LIST, fileList.stream().map(File::getName).toList());
    }

    @Test
    void testListFilesRecursively() {
        var fileList = IOUtils.listFilesRecursively(rootPath.toFile(), ".txt");
        assertNotNull(fileList);
        assertEquals(List.of("foo.txt"), fileList.stream().map(File::getName).toList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCreateTemporaryFileOrDirectory(boolean isDirectory) throws IOException {
        File tmpFile = IOUtils.createTemporaryFileOrDirectory(isDirectory, "prefix", "suffix");
        assertNotNull(tmpFile);
        assertTrue(tmpFile.exists());
        assertEquals(isDirectory, tmpFile.isDirectory());
        assertTrue(tmpFile.getAbsoluteFile().canWrite());
        assertTrue(tmpFile.getAbsoluteFile().canRead());
        Files.delete(tmpFile.toPath());
    }
}
