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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Assumes the comparison of {@link AnnotationData} objects works well.
     * This is being tested in {@link AnnotationDataTest} and {@link AnnotationLineTest}.
     */
    @Test
    void testSerialization() throws HistoryException {
        final String fileName = "main.c";
        Annotation annotation = new Annotation(fileName);
        annotation.addLine("1", "author1", true);
        annotation.addLine("2", "author1", true);
        File file = Paths.get(repositories.getSourceRoot(), "git", fileName).toFile();
        assertTrue(file.exists());
        cache.store(file, annotation);
        Annotation annotationFromCache = cache.readAnnotation(file);
        assertNotNull(annotationFromCache);
        assertEquals(annotation.annotationData, annotationFromCache.annotationData);
    }

    @Test
    void testGetNullLatestRev() {
        File file = new File(env.getSourceRootFile(), "foo");
        assertNull(LatestRevisionUtil.getLatestRevision(file));
        FileAnnotationCache cache = new FileAnnotationCache();
        assertNull(cache.get(file, null));
    }

    @Test
    void testClearFile() throws Exception {
        // Even though fake annotation is stored, this should be close to reality.
        final String fileName = "header.h";
        File file = Paths.get(repositories.getSourceRoot(), "git", fileName).toFile();
        assertTrue(file.exists());

        FileAnnotationCache cache = new FileAnnotationCache();
        Annotation annotation = new Annotation(fileName);
        annotation.addLine("1", "author1", true);
        annotation.addLine("2", "author1", true);
        cache.store(file, annotation);
        File cachedFile = cache.getCachedFile(file);
        assertTrue(cachedFile.exists());

        cache.clearFile(env.getPathRelativeToSourceRoot(file));
        assertFalse(cachedFile.exists());
        assertFalse(cachedFile.getParentFile().exists());
    }
}
