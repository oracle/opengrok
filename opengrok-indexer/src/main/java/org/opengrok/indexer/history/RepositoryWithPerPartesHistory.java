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

import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Statistics;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repositories extending this class will benefit from per partes history
 * indexing which saves memory.
 */
public abstract class RepositoryWithPerPartesHistory extends Repository {
    private static final long serialVersionUID = -3433255821312805064L;
    public static final int MAX_CHANGESETS = 128;

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWithPerPartesHistory.class);

    /**
     * Just like for {@link Repository#getHistory(File)} it is expected that the lists of (renamed) files
     * individual files (i.e. not directory) are empty.
     * @param file file to retrieve history for
     * @param sinceRevision start revision (non inclusive)
     * @param tillRevision end revision (inclusive)
     * @return history object
     * @throws HistoryException if history retrieval fails
     */
    abstract History getHistory(File file, String sinceRevision, String tillRevision) throws HistoryException;

    /**
     * @return maximum number of entries to retrieve
     */
    public int getPerPartesCount() {
        return MAX_CHANGESETS;
    }

    /**
     * Traverse the changesets using the visitor pattern.
     * @param sinceRevision start revision
     * @param visitor consumer of revisions
     * @throws HistoryException on error during history retrieval
     */
    public abstract void accept(String sinceRevision, Consumer<String> visitor) throws HistoryException;

    @Override
    protected void doCreateCache(HistoryCache cache, String sinceRevision, File directory) throws HistoryException {
        // For repositories that supports this, avoid storing complete History in memory
        // (which can be sizeable, at least for the initial indexing, esp. if merge changeset support is enabled),
        // by splitting the work into multiple chunks.
        BoundaryChangesets boundaryChangesets = new BoundaryChangesets(this);
        List<String> boundaryChangesetList = new ArrayList<>(boundaryChangesets.getBoundaryChangesetIDs(sinceRevision));
        boundaryChangesetList.add(null);    // to finish the last step in the cycle below
        LOGGER.log(Level.FINE, "boundary changesets: {0}", boundaryChangesetList);
        int cnt = 0;
        for (String tillRevision: boundaryChangesetList) {
            Statistics stat = new Statistics();
            LOGGER.log(Level.FINEST, "getting history for ({0}, {1})", new Object[]{sinceRevision, tillRevision});
            finishCreateCache(cache, getHistory(directory, sinceRevision, tillRevision), tillRevision);
            sinceRevision = tillRevision;

            stat.report(LOGGER, Level.FINE, String.format("finished chunk %d/%d of history cache for repository ''%s''",
                    ++cnt, boundaryChangesetList.size(), this.getDirectoryName()));
        }
    }
}
