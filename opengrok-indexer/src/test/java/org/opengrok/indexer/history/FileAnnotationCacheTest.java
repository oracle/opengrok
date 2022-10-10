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
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileAnnotationCacheTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repositories;
    private FileAnnotationCache cache;

    @BeforeEach
    public void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResource("/repositories"));

        // Needed for HistoryGuru to operate normally.
        env.setRepositories(repositories.getSourceRoot());
        env.setUseAnnotationCache(true);

        cache = new FileAnnotationCache();
        cache.initialize();
    }

    @AfterEach
    public void tearDown() {
        repositories.destroy();
        repositories = null;

        cache = null;
    }

    @Test
    void testSerialization() throws HistoryException {
        Annotation annotation = new Annotation("foo.txt");
        annotation.addLine("1", "author1", true);
        annotation.addLine("2", "author1", true);
        File file = Paths.get(repositories.getSourceRoot(), "git", "main.c").toFile();
        cache.store(file, annotation);
        Annotation annotation1 = cache.readAnnotation(file);
        assertEquals(annotation.annotationData, annotation1.annotationData);
    }
}
