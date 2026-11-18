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
package org.opengrok.indexer.analysis.groovy;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.XrefTestBase;

import java.io.IOException;

import static org.opengrok.indexer.util.StreamUtils.readTagsFromResource;

/**
 * Tests {@link GroovyXref} via {@link GroovyAnalyzerFactory}.
 */
class GroovyXrefTest extends XrefTestBase {

    @Test
    @SuppressWarnings("squid:S2699")
    void sampleTest() throws IOException {
        writeAndCompare(new GroovyAnalyzerFactory(),
                "analysis/groovy/sample.groovy",
                "analysis/groovy/sample_xref.html",
                readTagsFromResource("analysis/groovy/sampletags"), 104);
    }

    @Test
    @SuppressWarnings("squid:S2699")
    void shouldCloseTruncatedStringSpan() throws IOException {
        writeAndCompare(new GroovyAnalyzerFactory(),
                "analysis/groovy/truncated.groovy",
                "analysis/groovy/truncated_xref.html",
                null, 1);
    }
}