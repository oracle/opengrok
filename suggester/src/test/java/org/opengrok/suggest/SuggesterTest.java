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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class SuggesterTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullSuggesterDir() {
        new Suggester(null, 10, Duration.ofMinutes(5), false, true, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDuration() throws IOException {
        Path tempFile = Files.createTempFile("opengrok", "test");
        try {
            new Suggester(tempFile.toFile(), 10, null, false, true, null);
        } finally {
            tempFile.toFile().delete();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeDuration() throws IOException {
        Path tempFile = Files.createTempFile("opengrok", "test");
        try {
            new Suggester(tempFile.toFile(), 10, Duration.ofMinutes(-4), false, true, null);
        } finally {
            tempFile.toFile().delete();
        }
    }

}
