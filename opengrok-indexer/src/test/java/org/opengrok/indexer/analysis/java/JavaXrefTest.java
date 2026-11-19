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
package org.opengrok.indexer.analysis.java;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.CtagsReader;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.XrefTestBase;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.util.StreamUtils.readTagsFromResource;

/**
 * Tests the {@link JavaXref} class.
 */
class JavaXrefTest extends XrefTestBase {

    @Test
    @SuppressWarnings("squid:S2699")
    void sampleTest() throws IOException {
        writeAndCompare(new JavaAnalyzerFactory(),
                "analysis/java/Sample.jav",
                "analysis/java/sample_xref.html",
                readTagsFromResource("analysis/java/sampletags"), 32);
    }

    @Test
    @SuppressWarnings("squid:S2699")
    void shouldCloseTruncatedStringSpan() throws IOException {
        writeAndCompare(new JavaAnalyzerFactory(),
                "analysis/java/truncated.jav",
                "analysis/java/truncated_xref.html", null, 1);
    }

    @Test
    void shouldStyleRecordDefinition() throws IOException {
        String source = """
                package example;

                public record SearchHit(String path, LineRange range, double score) {}
                """;
        Definitions defs = parseTags("SearchHit\tSearchHit.java\t/^public record SearchHit"
                + "(String path, LineRange range, double score) {}$/;\""
                + "\trecord\tline:3\tsignature:(String path, LineRange range, double score)\n");

        AbstractAnalyzer analyzer = new JavaAnalyzerFactory().getAnalyzer();
        StringWriter out = new StringWriter();
        WriteXrefArgs args = new WriteXrefArgs(new StringReader(source), out);
        args.setDefs(defs);
        analyzer.writeXref(args);

        String xref = out.toString();
        assertTrue(xref.contains("<b>record</b>"));
        assertTrue(xref.contains("class=\"xr intelliWindow-symbol\""));
        assertTrue(xref.contains("[\"Record\",\"xr\",[[\"SearchHit\",3]]]"));
    }

    private static Definitions parseTags(String tags) {
        CtagsReader reader = new CtagsReader();
        tags.lines().forEach(reader::readLine);
        return reader.getDefinitions();
    }
}
