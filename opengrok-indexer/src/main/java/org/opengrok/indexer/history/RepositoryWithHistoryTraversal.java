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

import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Statistics;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class RepositoryWithHistoryTraversal extends RepositoryWithPerPartesHistory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWithHistoryTraversal.class);

    private static final long serialVersionUID = -1L;

    public static class CommitInfo {
        String revision;
        Date date;
        String authorName;
        String authorEmail;
        String message;

        CommitInfo(String revision, Date date, String authorName, String authorEmail, String message) {
            this.revision = revision;
            this.date = date;
            this.authorName = authorName;
            this.authorEmail = authorEmail;
            this.message = message;
        }
    }

    public static class ChangesetInfo {
        CommitInfo commit;
        public SortedSet<String> files;
        public Set<String> renamedFiles;
        public Set<String> deletedFiles;

        ChangesetInfo(CommitInfo commit) {
            this.commit = commit;
        }

        ChangesetInfo(CommitInfo commit, SortedSet<String> files, Set<String> renamedFiles, Set<String> deletedFiles) {
            this.commit = commit;
            this.files = files;
            this.renamedFiles = renamedFiles;
            this.deletedFiles = deletedFiles;
        }
    }

    /**
     * Traverse history of given file/directory.
     * @param file File object
     * @param sinceRevision start revision (non-inclusive)
     * @param tillRevision end revision (inclusive)
     * @param numCommits maximum number of commits to traverse (use {@code null} as unlimited)
     * @param visitors list of {@link ChangesetVisitor} objects
     * @throws HistoryException on error
     */
    public abstract void traverseHistory(File file, String sinceRevision, @Nullable String tillRevision,
                         Integer numCommits, List<ChangesetVisitor> visitors) throws HistoryException;

    @Override
    protected void doCreateCache(HistoryCache cache, String sinceRevision, File directory) throws HistoryException {
        // TODO: file collector should not be used if configuration says so
        FileCollector fileCollector = new FileCollector(true);

        if (!RuntimeEnvironment.getInstance().isHistoryCachePerPartesEnabled()) {
            LOGGER.log(Level.INFO, "repository {0} supports per partes history cache creation however " +
                    "it is disabled in the configuration. Generating history cache as whole.", this);

            HistoryCollector historyCollector = new HistoryCollector(false); // TODO: the flag should be based on configuration
            traverseHistory(directory, sinceRevision, null, null,
                    List.of(historyCollector, fileCollector));
            History history = new History(historyCollector.entries, historyCollector.renamedFiles);

            finishCreateCache(cache, history, null);

            RuntimeEnvironment.getInstance().setFileCollector(directory.getName(), fileCollector);

            return;
        }

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
            LOGGER.log(Level.FINEST, "storing history cache for revision range ({0}, {1})",
                    new Object[]{sinceRevision, tillRevision});

            HistoryCollector historyCollector = new HistoryCollector(false); // TODO: the flag should be based on configuration
            traverseHistory(directory, sinceRevision, tillRevision, null,
                    List.of(historyCollector, fileCollector));
            History history = new History(historyCollector.entries, historyCollector.renamedFiles);

            // Assign tags to changesets they represent
            // We don't need to check if this repository supports tags,
            // because we know it :-)
            if (RuntimeEnvironment.getInstance().isTagsEnabled()) {
                assignTagsInHistory(history);
            }

            finishCreateCache(cache, history, tillRevision);
            sinceRevision = tillRevision;
            stat.report(LOGGER, Level.FINE, String.format("finished chunk %d/%d of history cache for repository ''%s''",
                    ++cnt, boundaryChangesetList.size(), this.getDirectoryName()));
        }

        RuntimeEnvironment.getInstance().setFileCollector(directory.getName(), fileCollector);
    }
}
