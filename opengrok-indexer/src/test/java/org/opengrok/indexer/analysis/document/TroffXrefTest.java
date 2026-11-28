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
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.document;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.XrefTestBase;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link TroffXref} class.
 */
class TroffXrefTest extends XrefTestBase {

    @Test
    @SuppressWarnings("squid:S2699")
    void sampleTest() throws IOException {
        writeAndCompare(new TroffAnalyzerFactory(),
                "analysis/document/sync.1m",
                "analysis/document/sync_xref.html", null, 20);
    }

    @Test
    void escapedSpecialCharacterIsHtmlEncoded() throws IOException {
        TroffXref xref = new TroffXref(new StringReader("\\&<"));
        StringWriter out = new StringWriter();
        xref.write(out);
        assertTrue(out.toString().contains("&lt;"), out.toString());
    }

    @Override
    protected String getHtmlBegin() {
        return "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "<meta charset=\"UTF-8\">\n" +
            "<title>sampleFile - OpenGrok cross reference" +
            " for /sampleFile</title></head><body><pre>\n";
    }

    @Override
    protected String getHtmlEnd() {
        return "</pre></body>\n" +
            "</html>\n";
    }
}
