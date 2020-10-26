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
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.ruby;

import org.junit.Test;
import org.opengrok.indexer.analysis.XrefTestBase;
import org.opengrok.indexer.analysis.JFlexXref;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;
import static org.opengrok.indexer.util.StreamUtils.readTagsFromResource;

/**
 * Tests the {@link RubyXref} class.
 */
public class RubyXrefTest extends XrefTestBase {

    @Test
    public void sampleTest() throws IOException {
        writeAndCompare(new RubyAnalyzerFactory(),
                "analysis/ruby/sample.rb",
                "analysis/ruby/ruby_xrefres.html",
                readTagsFromResource("analysis/ruby/sampletags"), 161);
    }

    @Test
    public void colonQuoteAfterInterpolation() throws IOException {
        final String RUBY_COLON_QUOTE =
            "\"from #{logfn}:\"\n";
        JFlexXref xref = new JFlexXref(new RubyXref(new StringReader(
            RUBY_COLON_QUOTE)));

        StringWriter out = new StringWriter();
        xref.write(out);
        int actLOC = xref.getLOC();
        String xout = out.toString();

        final String xexpected = "<a class=\"l\" name=\"1\" href=\"#1\">1</a>"
                + "<span class=\"s\">&quot;from #{</span>"
                + "<a href=\"/source/s?defs=logfn\" "
                + "class=\"intelliWindow-symbol\" "
                + "data-definition-place=\"undefined-in-file\">logfn</a>"
                + "<span class=\"s\">}:&quot;</span>\n" +
            "<a class=\"l\" name=\"2\" href=\"#2\">2</a>\n";
        assertLinesEqual("Ruby colon-quote", xexpected, xout);
        assertEquals("Ruby colon-quote LOC", 1, actLOC);
    }
}
