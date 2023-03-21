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
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class HistoryCollector extends ChangesetVisitor {
    List<HistoryEntry> entries;
    Set<String> renamedFiles;

    HistoryCollector(boolean consumeMergeChangesets) {
        super(consumeMergeChangesets);
        entries = new ArrayList<>();
        renamedFiles = new HashSet<>();
    }

    @Override
    public void accept(RepositoryWithHistoryTraversal.ChangesetInfo changesetInfo) {
        RepositoryWithHistoryTraversal.CommitInfo commit = changesetInfo.commit;

        // TODO: add a test for this
        String author;
        if (commit.authorEmail != null) {
            author = commit.authorName + " <" + commit.authorEmail + ">";
        } else {
            author = commit.authorName;
        }

        HistoryEntry historyEntry = new HistoryEntry(
                commit.revision, commit.displayRevision,
                commit.date, author,
                commit.message, true, changesetInfo.files);

        if (changesetInfo.renamedFiles != null) {
            renamedFiles.addAll(changesetInfo.renamedFiles);
            // TODO: hack
            historyEntry.getFiles().addAll(changesetInfo.renamedFiles);
        }

        entries.add(historyEntry);
    }
}