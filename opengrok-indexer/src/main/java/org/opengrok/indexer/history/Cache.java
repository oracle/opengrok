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
 * Copyright (c) 2006, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.opengrok.indexer.logger.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

public interface Cache {

    Logger LOGGER = LoggerFactory.getLogger(Cache.class);

    /**
     * Get a string with information about the cache.
     *
     * @return a free form text string describing the history instance
     * @throws HistoryException if an error occurred while getting the info
     */
    String getInfo() throws HistoryException;

    /**
     * Clear the cache for a repository.
     *
     * @param repository the repository whose cache to clear
     * @throws HistoryException if the cache couldn't be cleared
     */
    void clear(RepositoryInfo repository) throws HistoryException;

    /**
     * Optimize how the history is stored on disk. This method is typically
     * called after the cache has been populated, or after large modifications
     * to the cache. The exact effect of this method is specific to each
     * implementation, but it may for example include compressing, compacting
     * or reordering the disk image of the cache in order to optimize
     * performance or space usage.
     *
     * @throws HistoryException if an error happens during optimization
     */
    void optimize() throws HistoryException;

    /**
     * Check whether this cache implementation can store history for the given repository.
     *
     * @param repository the repository to check
     * @return {@code true} if this cache implementation can store history
     * for the repository, or {@code false} otherwise
     */
    boolean supportsRepository(Repository repository);

    /**
     * @return directory name to be used to store cache files under data root
     */
    String getCacheDirName();

    /**
     * Remove cache data for a collection of repositories.
     * Note that this just deals with the data, the map used by {@link HistoryGuru}
     * will be left intact.
     *
     * @param repositories list of repository paths relative to source root
     * @return list of repository paths relative to source root for which the cache was successfully removed
     */
    List<String> clearCache(Collection<RepositoryInfo> repositories);

    /**
     * Check if the specified file is present in the cache.
     * @param file the file to check
     * @return {@code true} if the file is in the cache, {@code false} otherwise
     */
    boolean hasCacheForFile(File file) throws HistoryException;
}
