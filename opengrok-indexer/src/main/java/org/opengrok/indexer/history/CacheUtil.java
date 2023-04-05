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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper functions for {@link HistoryCache} and {@link AnnotationCache} implementations.
 */
public class CacheUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheUtil.class);

    private CacheUtil() {
        // private to enforce static
    }

    /**
     *
     * @param repository {@link RepositoryInfo} instance
     * @param cache {@link Cache} instance
     * @return absolute directory path for top level cache directory of given repository.
     * Will return {@code null} on error.
     */
    @VisibleForTesting
    @Nullable
    public static String getRepositoryCacheDataDirname(RepositoryInfo repository, Cache cache) {
        String repoDirBasename;

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        try {
            repoDirBasename = env.getPathRelativeToSourceRoot(new File(repository.getDirectoryName()));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    String.format("Could not resolve repository %s relative to source root", repository), ex);
            return null;
        } catch (ForbiddenSymlinkException ex) {
            LOGGER.log(Level.FINER, ex.getMessage());
            return null;
        }

        return env.getDataRootPath() + File.separatorChar
                + cache.getCacheDirName()
                + repoDirBasename;
    }

    public static void clearCacheDir(RepositoryInfo repository, Cache cache) {
        String histDir = CacheUtil.getRepositoryCacheDataDirname(repository, cache);
        if (histDir != null) {
            // Remove all files which constitute the history cache.
            try {
                IOUtils.removeRecursive(Paths.get(histDir));
            } catch (NoSuchFileException ex) {
                LOGGER.log(Level.WARNING, String.format("directory %s does not exist", histDir));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "tried removeRecursive()", ex);
            }
        }
    }
}
