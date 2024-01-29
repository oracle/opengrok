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
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is meant to collect files that were touched in some way by SCM update.
 * The visitor argument contains the files separated based on the type of modification performed,
 * however the consumer of this class is not interested in this classification.
 * This is because when incrementally indexing a bunch of changesets,
 * in one changeset a file may be deleted, only to be re-added in the next changeset etc.
 */
public class FileCollector extends ChangesetVisitor {
    private final Set<String> files;

    public FileCollector(boolean consumeMergeChangesets) {
        super(consumeMergeChangesets);
        files = new HashSet<>();
    }

    public void accept(RepositoryWithHistoryTraversal.ChangesetInfo changesetInfo) {
        if (changesetInfo.renamedFiles != null) {
            files.addAll(changesetInfo.renamedFiles);
        }
        if (changesetInfo.files != null) {
            files.addAll(changesetInfo.files);
        }
        if (changesetInfo.deletedFiles != null) {
            files.addAll(changesetInfo.deletedFiles);
        }
    }

    /**
     * @return set of file paths relative to source root. There are no guarantees w.r.t. ordering.
     */
    public Set<String> getFiles() {
        return files;
    }

    void addFiles(Collection<String> files) {
        this.files.addAll(files);
    }

    @VisibleForTesting
    public void reset() {
        files.clear();
    }
}