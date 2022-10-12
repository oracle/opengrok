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
 */
package org.opengrok.indexer.history;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.Statistics;

import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

public class FileAnnotationCache extends AbstractCache implements AnnotationCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileAnnotationCache.class);

    private static final ClassLoader classLoader = new AnnotationDataClassLoader();

    private Counter fileAnnotationCacheHits;
    private Counter fileAnnotationCacheMisses;

    private static final String ANNOTATION_CACHE_DIR_NAME = "annotationcache";

    public void initialize() {
        MeterRegistry meterRegistry = Metrics.getRegistry();
        if (meterRegistry != null) {
            fileAnnotationCacheHits = Counter.builder("cache.annotation.file.get").
                    description("file annotation cache hits").
                    tag("what", "hits").
                    register(meterRegistry);
            fileAnnotationCacheMisses = Counter.builder("cache.annotation.file.get").
                    description("file annotation cache misses").
                    tag("what", "miss").
                    register(meterRegistry);
        }
    }

    private static XMLDecoder getDecoder(InputStream in) {
        return new XMLDecoder(in, null, null, classLoader);
    }

    /**
     * Read annotation from a file.
     */
    static Annotation readCache(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             XMLDecoder d = getDecoder(new GZIPInputStream(new BufferedInputStream(in)))) {
            return new Annotation((AnnotationData) d.readObject());
        }
    }

    @VisibleForTesting
    Annotation readAnnotation(File file) {
        File cacheFile;
        try {
            cacheFile = getCachedFile(file);
        } catch (HistoryException | ForbiddenSymlinkException e) {
            LOGGER.log(Level.WARNING, "failed to get annotation cache file", e);
            return null;
        }

        try {
            return readCache(cacheFile);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("failed to read annotation cache for '%s'", file), e);
        }

        return null;
    }

    public Annotation get(File file, @Nullable String rev) {
        Annotation annotation = null;
        String latestRevision = LatestRevisionUtil.getLatestRevision(file);
        if (latestRevision != null && latestRevision.equals(rev)) {
            // read from the cache
            annotation = readAnnotation(file);
            if (annotation != null) {
                /*
                 * Double check that the cached annotation is not stale by comparing the stored revision
                 * with revision to be fetched.
                 * This should be more robust than the file time stamp based check performed by history cache,
                 * at the expense of having to read from the annotation cache.
                 */
                final String storedRevision = annotation.getRevision();
                if (storedRevision != null && !storedRevision.equals(rev)) {
                    LOGGER.log(Level.FINER,
                            "stored revision {0} for ''{1}'' does not match requested revision {2}",
                            new Object[]{storedRevision, file, rev});
                    annotation = null;
                }
            }
        }

        if (annotation != null) {
            if (fileAnnotationCacheHits != null) {
                fileAnnotationCacheHits.increment();
            }
        } else {
            if (fileAnnotationCacheMisses != null) {
                fileAnnotationCacheMisses.increment();
            }
            LOGGER.log(Level.FINEST, "annotation cache miss for ''{0}'' in revision {1}",
                    new Object[]{file, rev});
            return null;
        }

        return annotation;
    }

    public void store(File file, Annotation annotation) throws HistoryException {
        if (annotation.getRevision() == null) {
            throw new HistoryException(String.format("annotation for ''%s'' does not contain revision", file));
        }

        File cacheFile;
        try {
            cacheFile = getCachedFile(file);
        } catch (ForbiddenSymlinkException | HistoryException e) {
            LOGGER.log(Level.FINER, e.getMessage());
            return;
        }

        File dir = cacheFile.getParentFile();
        // calling isDirectory() twice to prevent a race condition
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new HistoryException("Unable to create cache directory '" + dir + "'.");
        }

        Statistics statistics = new Statistics();
        try {
            CacheUtil.writeCache(annotation.annotationData, cacheFile);
            statistics.report(LOGGER, Level.FINEST, String.format("wrote annotation for ''%s''", file),
                    "cache.annotation.file.store");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to write annotation to cache", e);
        }
    }

    public void clear(Repository repository) {
        CacheUtil.clearCacheDir(repository, this);
    }

    @Override
    public void optimize() {
        // nothing to do
    }

    @Override
    public boolean supportsRepository(Repository repository) {
        // all repositories are supported
        return true;
    }

    @Override
    public String getCacheDirName() {
        return ANNOTATION_CACHE_DIR_NAME;
    }

    @Override
    public String getInfo() throws HistoryException {
        return getClass().getSimpleName();
    }
}
