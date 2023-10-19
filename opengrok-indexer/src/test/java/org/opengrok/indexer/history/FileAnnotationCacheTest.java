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
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@link FileAnnotationCache}. These generally assume that the default
 * {@link AnnotationCache} implementation in {@link HistoryGuru} is {@link FileAnnotationCache}.
 */
class FileAnnotationCacheTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private TestRepository repositories;
    private FileAnnotationCache cache;

    @BeforeEach
    void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResource("/repositories"));

        // This needs to be set before the call to env.setRepositories() below as it instantiates HistoryGuru.
        env.setAnnotationCacheEnabled(true);

        // Needed for HistoryGuru to operate normally.
        env.setRepositories(repositories.getSourceRoot());

        cache = new FileAnnotationCache();
        cache.initialize();
    }

    @AfterEach
    void tearDown() {
        repositories.destroy();
        repositories = null;

        cache = null;
    }

    /**
     * Assumes the comparison of {@link AnnotationData} objects works well.
     * This is being tested in {@link AnnotationDataTest} and {@link AnnotationLineTest}.
     */
    @Test
    void testSerialization() throws Exception {
        final String fileName = "main.c";
        Annotation annotation = new Annotation(fileName);
        annotation.addLine("1.000", "author1", true, "1");
        annotation.addLine("2.000", "author1", true, "2");
        annotation.setRevision("2.000");
        File file = Paths.get(repositories.getSourceRoot(), "git", fileName).toFile();
        assertTrue(file.exists());
        cache.store(file, annotation);
        Annotation annotationFromCache = cache.readAnnotation(file);
        assertNotNull(annotationFromCache);
        assertEquals(annotation.annotationData, annotationFromCache.annotationData);
    }

    @Test
    void testReadAnnotationForNonexistentFile() throws Exception {
        final String fileName = "nonexistent";
        File file = Paths.get(repositories.getSourceRoot(), "git", fileName).toFile();
        assertFalse(file.exists());
        assertThrows(CacheException.class, () -> cache.readAnnotation(file));
    }

    @Test
    void testReadAnnotationNegativeCorruptedCacheFile() throws Exception {
        // Create annotation cache entry.
        final String fileName = "Makefile";
        File file = Paths.get(repositories.getSourceRoot(), "git", fileName).toFile();
        assertTrue(file.exists());
        Annotation annotation = new Annotation(fileName);
        annotation.addLine("1", "author1", true);
        annotation.addLine("2", "author1", true);
        annotation.setRevision("2");
        cache.store(file, annotation);
        File cachedFile = cache.getCachedFile(file);
        assertTrue(cachedFile.exists());

        // Corrupt the serialized annotation. Better test would be to corrupt the XML representation,
        // not just the compressed file.
        try (FileWriter writer = new FileWriter(cachedFile)) {
            writer.write("foo");
        }

        // Try to read the annotation from cache.
        assertThrows(CacheException.class, () -> cache.readAnnotation(file));
    }

    @Test
    void testClearFile() throws Exception {
        // Even though fake annotation is stored, this should be close to reality.
        final String fileName = "header.h";
        File file = Paths.get(repositories.getSourceRoot(), "git", fileName).toFile();
        assertTrue(file.exists());

        Annotation annotation = new Annotation(fileName);
        annotation.addLine("1", "author1", true);
        annotation.addLine("2", "author1", true);
        annotation.setRevision("2");
        cache.store(file, annotation);
        File cachedFile = cache.getCachedFile(file);
        assertTrue(cachedFile.exists());

        cache.clearFile(env.getPathRelativeToSourceRoot(file));
        assertFalse(cachedFile.exists());
        assertFalse(cachedFile.getParentFile().exists());
    }

    private static Stream<Arguments> getTestGetNullLatestRevParams() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, false),
                Arguments.of(true, true)
        );
    }

    /**
     * This is more test of {@link HistoryGuru#annotate(File, String)} than {@link FileAnnotationCache},
     * however in {@link HistoryGuruTest} the annotation cache is not directly accessible,
     * so the test resides here.
     * <p>
     * Specifically, the test ensures that annotation for the last revision
     * (specified with the <code>null</code> revision argument) retrieved from repository
     * has the correct non-<code>null</code> revision filled in and the revision is preserved in the cache.
     * </p>
     */
    @ParameterizedTest
    @MethodSource("getTestGetNullLatestRevParams")
    void testGetNullLatestRev(boolean storeViaHistoryGuru, boolean nullLatestRevision) throws Exception {
        // Make sure the file exists and its last revision can be determined.
        // The latter is important later on for checking whether the annotation
        // gets stored along with the last revision.
        File file = Paths.get(env.getSourceRootPath(), "git", "main.c").toFile();
        assertTrue(file.exists());
        String latestRev = LatestRevisionUtil.getLatestRevision(file);
        assertNotNull(latestRev);

        // Make sure there is clean state. The file should not have any cache entry.
        FileAnnotationCache cache = new FileAnnotationCache();
        cache.clearFile(env.getPathRelativeToSourceRoot(file));
        assertThrows(CacheException.class, () -> cache.get(file, null));

        // Store the annotation in the cache.
        HistoryGuru historyGuru = HistoryGuru.getInstance();
        Annotation annotation;
        if (storeViaHistoryGuru) {
            annotation = historyGuru.getRepository(file).annotate(file, null);
            assertNotNull(annotation);
            // Repository method does not set the revision, so it has to be set here.
            assertNull(annotation.getRevision());
            annotation.setRevision(latestRev);
            historyGuru.createAnnotationCache(file, latestRev);
        } else {
            annotation = historyGuru.annotate(file, null);
            assertNotNull(annotation);
            assertNotNull(annotation.getRevision());
            cache.store(file, annotation);
        }

        // Retrieve annotation directly from the cache.
        Annotation cachedAnnotation;
        if (nullLatestRevision) {
            cachedAnnotation = cache.get(file, null);
        } else {
            cachedAnnotation = cache.get(file, latestRev);
        }
        assertNotNull(cachedAnnotation);
        assertNotNull(cachedAnnotation.getRevision());
        assertEquals(latestRev, cachedAnnotation.getRevision());
        assertEquals(annotation.annotationData, cachedAnnotation.annotationData);
    }

    /**
     * Test that the {@link Repository#isAnnotationCacheEnabled()} is honored when creating history cache.
     * Specifically that repository can override global setting.
     */
    @Test
    void testRepositoryDisabledAnnotationCache() throws Exception {
        HistoryGuru historyGuru = HistoryGuru.getInstance();
        assertTrue(env.isAnnotationCacheEnabled());
        assertFalse(historyGuru.getAnnotationCacheInfo().startsWith("No"));

        File file = Paths.get(env.getSourceRootPath(), "git", "main.c").toFile();
        assertTrue(file.exists());

        // Make sure there is clean state. The file should not have any cache entry.
        FileAnnotationCache cache = new FileAnnotationCache();
        cache.clearFile(env.getPathRelativeToSourceRoot(file));
        assertThrows(CacheException.class, () -> cache.get(file, null));

        Repository repository = historyGuru.getRepository(file);
        assertNotNull(repository);

        repository.setAnnotationCacheEnabled(false);
        String latestRev = LatestRevisionUtil.getLatestRevision(file);
        assertThrows(CacheException.class,
                () -> historyGuru.createAnnotationCache(file, latestRev));
        repository.setAnnotationCacheEnabled(false);
    }

    private enum revisionType {
        NULL,
        LATEST,
        INVALID
    };

    /**
     * Make sure stale check via revision string comparison works.
     */
    @ParameterizedTest
    @EnumSource(revisionType.class)
    void testGeRevisionMismatch(revisionType revisionType) throws Exception {
        // Even though fake annotation is stored, the file has to be real because eventually
        // its latest revision is to be fetched in cache.get().
        final String fileName = "header.h";
        File file = Paths.get(repositories.getSourceRoot(), "git", fileName).toFile();
        assertTrue(file.exists());

        // Store annotation for particular revision.
        final String revision = "2";
        FileAnnotationCache cache = new FileAnnotationCache();
        Annotation annotation = new Annotation(fileName);
        annotation.addLine("1", "author1", true);
        annotation.addLine("2", "author1", true);
        annotation.setRevision(revision);
        cache.store(file, annotation);
        File cachedFile = cache.getCachedFile(file);
        assertTrue(cachedFile.exists());

        // Try to retrieve different revision of the file.
        switch (revisionType) {
            case NULL:
                assertNull(cache.get(file, null));
                break;
            case LATEST:
                String latestRev = LatestRevisionUtil.getLatestRevision(file);
                assertNull(cache.get(file,  latestRev));
                break;
            case INVALID:
                assertNull(cache.get(file, revision + "1"));
                break;
            default:
                fail("Invalid value");
        }
    }
}
