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
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis;

import org.apache.lucene.document.Document;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Class to wrap Xref production with timeout. This should be used for all classes that override
 * {@link FileAnalyzer#analyze(Document, StreamSource, Writer)}.
 */
public class XrefWork {
    private Xrefer xrefer;
    private Exception exception;
    private final WriteXrefArgs args;
    private final AbstractAnalyzer analyzer;

    public XrefWork(WriteXrefArgs args, AbstractAnalyzer analyzer) {
        this.args = args;
        this.analyzer = analyzer;
    }

    public Xrefer getXrefer() throws ExecutionException, InterruptedException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        CompletableFuture<XrefWork> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        xrefer = this.analyzer.writeXref(args);
                    } catch (IOException e) {
                        exception = e;
                    }
                    return this;
                }, env.getIndexerParallelizer().getXrefWatcherExecutor()).
                orTimeout(env.getXrefTimeout(), TimeUnit.SECONDS);

        XrefWork xrefWork = future.get(); // Will throw ExecutionException wrapping TimeoutException on timeout.
        if (xrefWork.xrefer != null) {
            return xrefer;
        } else {
            // Re-throw the exception from writeXref().
            throw new ExecutionException(xrefWork.exception);
        }
    }
}
