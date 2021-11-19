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

import org.jetbrains.annotations.TestOnly;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Statistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class to split sequence of VCS changesets into number of intervals.
 * This is then used in {@link Repository#createCache(HistoryCache, String)}
 * to store history in chunks, for VCS repositories that support this.
 */
public class BoundaryChangesets {
    private int cnt = 0;
    private final List<String> result = new ArrayList<>();

    private final int maxCount;
    private final RepositoryWithPerPartesHistory repository;

    private static final Logger LOGGER = LoggerFactory.getLogger(BoundaryChangesets.class);

    public BoundaryChangesets(RepositoryWithPerPartesHistory repository) {
        this.repository = repository;

        int globalPerPartesCount = RuntimeEnvironment.getInstance().getHistoryChunkCount();
        if (globalPerPartesCount > 0) {
            this.maxCount = globalPerPartesCount;
        } else {
            this.maxCount = repository.getPerPartesCount();
        }
        if (maxCount <= 1) {
            throw new RuntimeException(String.format("per partes count for repository ''%s'' " +
                    "must be strictly greater than 1", repository.getDirectoryName()));
        }
        LOGGER.log(Level.FINER, "using history cache chunks with {0} entries for repository {1}",
                new Object[]{this.maxCount, repository});
    }

    private void reset() {
        cnt = 0;
        result.clear();
    }

    @TestOnly
    int getMaxCount() {
        return maxCount;
    }

    /**
     * @param sinceRevision start revision ID
     * @return immutable list of revision IDs denoting the intervals
     * @throws HistoryException if there is problem traversing the changesets in the repository
     */
    public synchronized List<String> getBoundaryChangesetIDs(String sinceRevision) throws HistoryException {
        reset();

        LOGGER.log(Level.FINE, "getting boundary changesets for ''{0}''", repository.getDirectoryName());
        Statistics stat = new Statistics();

        repository.accept(sinceRevision, this::visit);

        // The changesets need to go from oldest to newest.
        Collections.reverse(result);

        stat.report(LOGGER, Level.FINE,
                String.format("Done getting boundary changesets for ''%s'' (%d entries)",
                        repository.getDirectoryName(), result.size()));

        return List.copyOf(result);
    }

    private void visit(String id) {
        if (cnt != 0 && cnt % maxCount == 0) {
            result.add(id);
        }
        cnt++;
    }
}
