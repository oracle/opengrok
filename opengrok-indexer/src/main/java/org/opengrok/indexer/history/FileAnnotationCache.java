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

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Statistics;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileAnnotationCache extends AbstractCache implements AnnotationCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileAnnotationCache.class);

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

    /**
     * Read serialized {@link AnnotationData} from a file and create {@link Annotation} instance out of it.
     */
    static Annotation readCache(File file) throws IOException {
        ObjectMapper mapper = new SmileMapper();
        return new Annotation(mapper.readValue(file, AnnotationData.class));
    }

    /**
     * Retrieve revision from the cache for given file. This is done in a fashion that keeps I/O low.
     * Assumes that {@link AnnotationData#revision} is serialized in the cache file as the first member.
     * @param file source root file
     * @return revision from the cache file or {@code null}
     * @throws CacheException on error
     */
    @Nullable
    String getRevision(File file) throws CacheException {
        File cacheFile;
        try {
            cacheFile = getCachedFile(file);
        } catch (CacheException e) {
            throw new CacheException("failed to get annotation cache file", e);
        }

        SmileFactory factory = new SmileFactory();
        try (SmileParser parser = factory.createParser(cacheFile)) {
            parser.nextToken();
            while (parser.getCurrentToken() != null) {
                if (parser.getCurrentToken().equals(JsonToken.FIELD_NAME)) {
                    break;
                }
                parser.nextToken();
            }

            if (parser.getCurrentName().equals("revision")) {
                parser.nextToken();
                if (!parser.getCurrentToken().equals(JsonToken.VALUE_STRING)) {
                    LOGGER.log(Level.WARNING, "the value of the ''revision'' field in ''{0}'' is not string",
                            cacheFile);
                    return null;
                }
                return parser.getValueAsString();
            } else {
                LOGGER.log(Level.WARNING, "the first serialized field is not ''revision'' in ''{0}''", cacheFile);
                return null;
            }
        } catch (IOException e) {
            throw new CacheException(e);
        }
    }

    @Override
    public String getCacheFileSuffix() {
        return "";
    }

    @VisibleForTesting
    Annotation readAnnotation(File file) throws CacheException {
        File cacheFile;
        try {
            cacheFile = getCachedFile(file);
        } catch (CacheException e) {
            LOGGER.log(Level.WARNING, "failed to get annotation cache file", e);
            return null;
        }

        try {
            Statistics statistics = new Statistics();
            Annotation annotation = readCache(cacheFile);
            statistics.report(LOGGER, Level.FINEST, String.format("deserialized annotation from cache for '%s'", file));
            return annotation;
        } catch (IOException e) {
            throw new CacheException(String.format("failed to read annotation cache for '%s'", file), e);
        }
    }

    /**
     * This is potentially expensive operation as the cache entry has to be retrieved from disk
     * in order to tell whether it is stale or not.
     * @param file source file
     * @return indication whether the cache entry is fresh
     */
    public boolean isUpToDate(File file) {
        try {
            return get(file, null) != null;
        } catch (CacheException e) {
            return false;
        }
    }

    public Annotation get(File file, @Nullable String rev) throws CacheException {
        Annotation annotation = null;
        String latestRevision = LatestRevisionUtil.getLatestRevision(file);
        if (rev == null || (latestRevision != null && latestRevision.equals(rev))) {
            /*
             * Double check that the cached annotation is not stale by comparing the stored revision
             * with revision to be fetched.
             * This should be more robust than the file time stamp based check performed by history cache,
             * at the expense of having to read some content from the annotation cache.
             */
            final String storedRevision = getRevision(file);
            /*
             * Even though store() does not allow to store annotation with null revision, the check
             * should be present to catch weird cases of someone not using the store() or general badness.
             */
            if (storedRevision == null) {
                LOGGER.log(Level.FINER, "no stored revision in annotation cache for ''{0}''", file);
            } else if (!storedRevision.equals(latestRevision)) {
                LOGGER.log(Level.FINER,
                        "stored revision {0} for ''{1}'' does not match latest revision {2}",
                        new Object[]{storedRevision, file, rev});
            } else {
                // read from the cache
                annotation = readAnnotation(file);
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

    private void writeCache(AnnotationData annotationData, File outfile) throws IOException {
        ObjectMapper mapper = new SmileMapper();
        mapper.writeValue(outfile, annotationData);
    }

    public void store(File file, Annotation annotation) throws CacheException {
        if (annotation.getRevision() == null || annotation.getRevision().isEmpty()) {
            throw new CacheException(String.format("annotation for ''%s'' does not contain revision", file));
        }

        File cacheFile;
        try {
            cacheFile = getCachedFile(file);
        } catch (CacheException e) {
            LOGGER.log(Level.FINER, e.getMessage());
            return;
        }

        File dir = cacheFile.getParentFile();
        // calling isDirectory() twice to prevent a race condition
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new CacheException("Unable to create cache directory '" + dir + "'.");
        }

        Statistics statistics = new Statistics();
        try {
            writeCache(annotation.annotationData, cacheFile);
            statistics.report(LOGGER, Level.FINEST, String.format("wrote annotation for '%s'", file),
                    "cache.annotation.file.store.latency");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to write annotation to cache", e);
        }
    }

    public void clear(RepositoryInfo repository) {
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
    public String getInfo() {
        return getClass().getSimpleName();
    }
}
