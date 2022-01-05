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

import java.io.File;
import java.util.Date;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.util.ForbiddenSymlinkException;

interface HistoryCache {
    /**
     * Create and initialize an empty history cache if one doesn't exist
     * already.
     *
     * @throws HistoryException if initialization fails
     */
    void initialize() throws HistoryException;

    /**
     * Check whether this cache implementation can store history for the given
     * repository.
     *
     * @param repository the repository to check
     * @return {@code true} if this cache implementation can store history
     * for the repository, or {@code false} otherwise
     */
    boolean supportsRepository(Repository repository);

    /**
     * Retrieve the history for the given file, either from the cache or by
     * parsing the history information in the repository.
     *
     * @param file The file to retrieve history for
     * @param repository The external repository to read the history from (can be <code>null</code>)
     * @param withFiles A flag saying whether the returned history should include a list of files
     *                  touched by each changeset. If false, the implementation is allowed to skip the file list,
     *                  but it doesn't have to.
     * @param fallback whether to fall back to {@link Repository#getHistory(File)}
     *                 if the history cannot be retrieved from the cache
     * @throws HistoryException if the history cannot be fetched
     * @throws ForbiddenSymlinkException if symbolic-link checking encounters
     * an ineligible link
     */
    History get(File file, @Nullable Repository repository, boolean withFiles, boolean fallback)
            throws HistoryException, ForbiddenSymlinkException;

    /**
     * Usually a wrapper of {@link HistoryCache#get(File, Repository, boolean, boolean)}.
     */
    History get(File file, @Nullable Repository repository, boolean withFiles)
            throws HistoryException, ForbiddenSymlinkException;

    /**
     * Store the history for a repository.
     *
     * @param history The history to store
     * @param repository The repository whose history to store
     * @throws HistoryException if the history cannot be stored
     */
    void store(History history, Repository repository) throws HistoryException;

    /**
     * Store potentially partial history for a repository.
     *
     * @param history The history to store
     * @param repository The repository whose history to store
     * @param tillRevision end revision (can be null)
     * @throws HistoryException if the history cannot be stored
     */
    void store(History history, Repository repository, String tillRevision) throws HistoryException;

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
     * Check if the specified file is present in the cache.
     * @param file the file to check
     * @return {@code true} if the file is in the cache, {@code false}
     * otherwise
     */
    boolean hasCacheForFile(File file) throws HistoryException;

    /**
     * Get the revision identifier for the latest cached revision in a
     * repository.
     *
     * @param repository the repository whose latest revision to return
     * @return a string representing the latest revision in the cache, or
     * {@code null} if it is unknown
     */
    String getLatestCachedRevision(Repository repository)
            throws HistoryException;

    /**
     * Get the last modified times for all files and subdirectories in the
     * specified directory.
     *
     * @param directory which directory to fetch modification times for
     * @param repository the repository in which the directory lives
     * @return a map from file names to modification times
     */
    Map<String, Date> getLastModifiedTimes(
            File directory, Repository repository)
        throws HistoryException;

    /**
     * Clear the history cache for a repository.
     *
     * @param repository the repository whose cache to clear
     * @throws HistoryException if the cache couldn't be cleared
     */
    void clear(Repository repository) throws HistoryException;

    /**
     * Clear entry for single file from history cache.
     * @param file path to the file relative to the source root
     */
    void clearFile(String file);

    /**
     * Get a string with information about the history cache.
     *
     * @return a free form text string describing the history cache instance
     * @throws HistoryException if an error occurred while getting the info
     */
    String getInfo() throws HistoryException;

    // Set and query if history index phase is done.
    void setHistoryIndexDone();
    boolean isHistoryIndexDone();
}
