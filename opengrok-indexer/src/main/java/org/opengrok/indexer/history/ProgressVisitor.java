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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.opengrok.indexer.util.Progress;

public class ProgressVisitor extends ChangesetVisitor {

    private final Progress progress;

    ProgressVisitor(Progress progress) {
        super(true);

        this.progress = progress;
    }

    public void accept(RepositoryWithHistoryTraversal.ChangesetInfo changesetInfo) {
        progress.increment();
    }
}
