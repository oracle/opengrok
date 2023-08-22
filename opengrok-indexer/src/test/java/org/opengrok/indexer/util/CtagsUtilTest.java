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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Represents a container for tests of {@link CtagsUtil}.
 */
class CtagsUtilTest {

    @Test
    void getLanguages() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        Set<String> result = CtagsUtil.getLanguages(env.getCtags());
        assertNotNull(result, "getLanguages() should always return non null");
        assertFalse(result.isEmpty(), "should get Ctags languages");
        assertTrue(result.contains("C++"), "Ctags languages should contain C++");
    }

    @Test
    void validate() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        Path tmpSourceRoot = Files.createTempDirectory("srcRootCtagsValidationTest");
        env.setSourceRoot(tmpSourceRoot.toString());
        assertTrue(env.getSourceRootFile().exists());

        assertTrue(CtagsUtil.validate(env.getCtags()));

        Files.delete(tmpSourceRoot);
    }

    @Test
    void testValidateWithInvalidExtraOptions() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        Path tmpSourceRoot = Files.createTempDirectory("srcRootCtagsValidationTestExtraArgs");
        env.setSourceRoot(tmpSourceRoot.toString());
        assertTrue(env.getSourceRootFile().exists());

        Path extraOptionsPath = Path.of(env.getSourceRootPath(), "extra.config");
        Files.write(extraOptionsPath, List.of("--fooBar"));
        String extraOptionsAbsPath = extraOptionsPath.toAbsolutePath().toString();

        env.setCTagsExtraOptionsFile(extraOptionsAbsPath);
        assertFalse(CtagsUtil.validate(env.getCtags()));

        // cleanup
        env.setCTagsExtraOptionsFile(null);
        Files.delete(extraOptionsPath);
        Files.delete(tmpSourceRoot);
    }
}
