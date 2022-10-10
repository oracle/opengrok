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
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.configuration.PathAccepter;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ForbiddenSymlinkException;

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

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    // TODO
    private final PathAccepter pathAccepter = env.getPathAccepter();

    FileAnnotationCache() {
        // TODO
    }

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
        // TODO: use single AnnotationDataClassLoader instance ?
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

        // TODO: should be used elsewhere ? in store() ?
        //if (!pathAccepter.accept(file)) {
        //    return null;
        //}

        return null;
    }

    public Annotation get(File file, String rev) {
        Annotation ret = null;
        if (LatestRevisionUtil.getLatestRevision(file).equals(rev)) {
            // read from the cache
            ret = readAnnotation(file);
            if (ret != null) {
                // Double check that the cached annotation is not stale.
                // TODO: what about file time stamp based check ?
                final String storedRevision = ret.getRevision();
                if (storedRevision != null && !storedRevision.equals(rev)) {
                    LOGGER.log(Level.FINER, "stored revision {0} does not match requested revision {1}",
                            new Object[]{storedRevision, rev});
                    ret = null;
                }
            }
        }

        // TODO: remove the stale cache entry ?
        // need to state the assumptions on how the cache entry could become invalid.
        // - certainly during indexing

        if (ret != null) {
            if (fileAnnotationCacheHits != null) {
                fileAnnotationCacheHits.increment();
            }
        } else {
            if (fileAnnotationCacheMisses != null) {
                fileAnnotationCacheMisses.increment();
            }
            LOGGER.log(Level.FINEST, "annotation cache miss for {0} in revision {1}", new Object[]{file, rev});
            return null;
        }

        return ret;
    }

    @Override
    public void store(File file, Annotation annotation) {
        File cacheFile;
        try {
            cacheFile = getCachedFile(file);
        } catch (ForbiddenSymlinkException | HistoryException e) {
            LOGGER.log(Level.FINER, e.getMessage());
            return;
        }

        File dir = cacheFile.getParentFile();
        // calling isDirectory twice to prevent a race condition
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            // throw new HistoryException("Unable to create cache directory '" + dir + "'.");
            LOGGER.log(Level.WARNING, "Unable to create cache directory");
            return;
        }

        File outputFile = cacheFile;
        try {
            CacheUtil.writeCache(annotation.annotationData, outputFile);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to write annotation to cache", e);
        }
    }

    @Override
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
