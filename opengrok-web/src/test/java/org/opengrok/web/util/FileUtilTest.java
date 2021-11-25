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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Represents a container for tests of {@link FileUtil}.
 */
class FileUtilTest {

    RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Test
    void shouldThrowOnNullArgument() {
        assertThrows(NoPathParameterException.class, () -> FileUtil.toFile(null),
                "toFile(null)");
    }

    private String setSourceRoot(String id) throws IOException {
        String origRoot = env.getSourceRootPath();
        Path dir = Files.createTempDirectory(id);
        dir.toFile().deleteOnExit();
        env.setSourceRoot(dir.toString());
        assertTrue(env.getSourceRootFile().isDirectory());
        return origRoot;
    }

    @Test
    void shouldThrowOnInvalidFile() throws IOException {
        String origRoot = setSourceRoot("shouldThrowOnInvalidFile");
        String rndPath = ".." + File.separator + UUID.randomUUID();
        assertThrows(InvalidPathException.class, () -> FileUtil.toFile(rndPath),
                "toFile(randomUUID)");
        env.setSourceRoot(origRoot);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldThrowOnMissingFile(boolean isPresent) throws IOException, NoPathParameterException {
        String origRoot = setSourceRoot("shouldThrowOnMissingFile");
        if (isPresent) {
            String fileName = "existent";
            Path filePath = Paths.get(env.getSourceRootPath(), fileName);
            Files.createFile(filePath);
            filePath.toFile().deleteOnExit();
            assertTrue(filePath.toFile().exists());
            assertNotNull(FileUtil.toFile(fileName));
        } else {
            assertThrows(FileNotFoundException.class, () -> FileUtil.toFile("nonexistent"),
                    "toFile(nonexistent)");
        }
        env.setSourceRoot(origRoot);
    }
}
