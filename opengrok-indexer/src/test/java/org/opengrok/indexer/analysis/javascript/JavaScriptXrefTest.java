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
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.javascript;

import static org.opengrok.indexer.util.StreamUtils.readTagsFromResource;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.XrefTestBase;

/**
 * Tests the {@link JavaScriptXref} class.
 */
class JavaScriptXrefTest extends XrefTestBase {

    @Test
    @SuppressWarnings("squid:S2699")
    void sampleTest() throws IOException {
        writeAndCompare(new JavaScriptAnalyzerFactory(),
                "analysis/javascript/sample.js",
                "analysis/javascript/sample_xref.html",
                readTagsFromResource("analysis/javascript/sampletags"), 206);
    }

    @Test
    @SuppressWarnings("squid:S2699")
    void shouldCloseTruncatedStringSpan() throws IOException {
        writeAndCompare(new JavaScriptAnalyzerFactory(),
                "analysis/javascript/truncated.js",
                "analysis/javascript/truncated_xref.html", null, 1);
    }

    @Test
    @SuppressWarnings("squid:S2699")
    void shouldDetectRegularExpressionWithoutModifiers() throws IOException {
        writeAndCompare(new JavaScriptAnalyzerFactory(),
                "analysis/javascript/regexp_plain.js",
                "analysis/javascript/regexp_plain_xref.html", null, 14);
    }

    @Test
    @SuppressWarnings("squid:S2699")
    void shouldDetectRegularExpressionWithModifiers() throws IOException {
        writeAndCompare(new JavaScriptAnalyzerFactory(),
                "analysis/javascript/regexp_modifiers.js",
                "analysis/javascript/regexp_modifiers_xref.html", null, 14);
    }
}
