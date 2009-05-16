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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.File;

interface HistoryCache {

    /**
     * Create and initialize an empty history cache if one doesn't exist
     * already.
     *
     * @throws HistoryException if initalization fails
     */
    void initialize() throws HistoryException;

    /**
     * Retrieve the history for the given file, either from the cache or by
     * parsing the history information in the repository.
     *
     * @param file The file to retrieve history for
     * @param parserClass The class that implements the parser to use
     * @param repository The external repository to read the history from (can
     * be <code>null</code>)
     * @throws HistoryException if the history cannot be fetched
     */
    History get(File file, Repository repository) throws HistoryException;

    /**
     * Store the history for a repository.
     * 
     * @param history The history to store
     * @param repository The repository whose history to store
     * @throws HistoryException if the history cannot be stored
     */
    void store(History history, Repository repository)
            throws HistoryException;

    /**
     * Check if the cache is up to date for the specified file.
     * @param file the file to check
     * @param repository the repository in which the file is stored
     * @return {@code true} if the cache is up to date, {@code false} otherwise
     */
    boolean isUpToDate(File file, Repository repository)
            throws HistoryException;

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
}
