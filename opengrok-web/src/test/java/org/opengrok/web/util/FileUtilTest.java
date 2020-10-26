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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.util;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import java.io.FileNotFoundException;
import java.util.UUID;

/**
 * Represents a container for tests of {@link FileUtil}.
 */
public class FileUtilTest {

    @Test
    public void shouldThrowOnNullArgument() throws FileNotFoundException {
        assertThrows(NoPathParameterException.class, () -> FileUtil.toFile(null),
                "toFile(null)");
    }

    @Test
    public void shouldThrowOnMissingFile() throws NoPathParameterException {
        assertThrows(FileNotFoundException.class, () -> FileUtil.toFile(
                UUID.randomUUID().toString()), "toFile(randomUUID)");
    }
}
