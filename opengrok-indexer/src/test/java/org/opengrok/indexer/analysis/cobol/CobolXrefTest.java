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
 * Copyright (c) 2026, Siyabend Urun <urunsiyabend@gmail.com>.
 */
package org.opengrok.indexer.analysis.cobol;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.XrefTestBase;

import java.io.IOException;

/**
 * Tests {@link CobolFixedXref} and {@link CobolFreeXref} via the analyzer
 * factory. Each test pins the format up-front because {@link XrefTestBase}
 * drives {@code writeXref} directly and bypasses the sniff that normally runs
 * inside {@code analyze}.
 */
class CobolXrefTest extends XrefTestBase {

    @Test
    void fixedFormatSampleTest() throws IOException {
        writeAndCompare(factory(true),
                "analysis/cobol/sample-fixed.cbl",
                "analysis/cobol/sample-fixed_xref.html",
                null, 21);
    }

    @Test
    void freeFormatSampleTest() throws IOException {
        writeAndCompare(factory(false),
                "analysis/cobol/sample-free.cbl",
                "analysis/cobol/sample-free_xref.html",
                null, 19);
    }

    @Test
    void fixedFormatTruncatedStringClosesSpan() throws IOException {
        writeAndCompare(factory(true),
                "analysis/cobol/truncated-fixed.cbl",
                "analysis/cobol/truncated-fixed_xref.html",
                null, 1);
    }

    @Test
    void freeFormatTruncatedStringClosesSpan() throws IOException {
        writeAndCompare(factory(false),
                "analysis/cobol/truncated-free.cbl",
                "analysis/cobol/truncated-free_xref.html",
                null, 1);
    }

    /**
     * Returns a factory whose per-thread cached analyzer has the format flag
     * pinned. {@link XrefTestBase#writeAndCompare} later calls
     * {@code factory.getAnalyzer()} on the same thread and gets the same
     * instance, so the pinned flag survives.
     */
    private static CobolAnalyzerFactory factory(boolean isFixed) {
        CobolAnalyzerFactory f = new CobolAnalyzerFactory();
        ((CobolAnalyzer) f.getAnalyzer()).setFixedFormat(isFixed);
        return f;
    }
}
