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

import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.Progress;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

class MercurialHistoryParserRevisionsOnly implements Executor.StreamHandler {
    private final MercurialRepository repository;
    private final Consumer<BoundaryChangesets.IdWithProgress> visitor;

    private final Progress progress;

    MercurialHistoryParserRevisionsOnly(MercurialRepository repository,
                                        Consumer<BoundaryChangesets.IdWithProgress> visitor, Progress progress) {
        this.repository = repository;
        this.visitor = visitor;
        this.progress = progress;
    }

    void parse(File file, String sinceRevision) throws HistoryException {
        try {
            Executor executor = repository.getHistoryLogExecutor(file, sinceRevision, null, true);
            int status = executor.exec(true, this);

            if (status != 0) {
                throw new HistoryException(
                        String.format("Failed to get revisions for: \"%s\" since revision %s Exit code: %d",
                                file.getAbsolutePath(), sinceRevision, status));
            }
        } catch (IOException e) {
            throw new HistoryException("Failed to get history for: \"" +
                    file.getAbsolutePath() + "\"", e);
        }
    }

    @Override
    public void processStream(InputStream input) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(input))) {
            String s;
            while ((s = in.readLine()) != null) {
                visitor.accept(new BoundaryChangesets.IdWithProgress(s, progress));
            }
        }
    }
}
