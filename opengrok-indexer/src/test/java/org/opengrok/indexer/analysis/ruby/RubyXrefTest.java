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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.ruby;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.opengrok.indexer.analysis.CtagsReader;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.FileAnalyzer;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.Xrefer;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;
import static org.opengrok.indexer.util.StreamUtils.copyStream;

/**
 * Tests the {@link RubyXref} class.
 */
public class RubyXrefTest {

    @Test
    public void sampleTest() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "analysis/ruby/sample.rb");
        assertNotNull("though sample.rb should stream,", res);
        int actLOC = writeRubyXref(res, new PrintStream(baos));
        res.close();

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
            "analysis/ruby/ruby_xrefres.html");
        assertNotNull("ruby_xrefres.html should stream,", exp);
        byte[] expbytes = copyStream(exp);
        exp.close();
        baos.close();

        String ostr = new String(baos.toByteArray(), "UTF-8");
        String estr = new String(expbytes, "UTF-8");
        assertLinesEqual("Ruby xref", estr, ostr);
        assertEquals("Ruby LOC", 159, actLOC);
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

    private int writeRubyXref(InputStream iss, PrintStream oss)
            throws IOException {
        oss.print(getHtmlBegin());

        Writer sw = new StringWriter();
        RubyAnalyzerFactory fac = new RubyAnalyzerFactory();
        FileAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs wargs = new WriteXrefArgs(
            new InputStreamReader(iss, "UTF-8"), sw);
        wargs.setDefs(getTagsDefinitions());
        Xrefer xref = analyzer.writeXref(wargs);

        oss.print(sw.toString());
        oss.print(getHtmlEnd());
        return xref.getLOC();
    }

    private Definitions getTagsDefinitions() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "analysis/ruby/sampletags");
        assertNotNull("though sampletags should stream,", res);

        BufferedReader in = new BufferedReader(new InputStreamReader(
            res, "UTF-8"));

        CtagsReader rdr = new CtagsReader();
        String line;
        while ((line = in.readLine()) != null) {
            rdr.readLine(line);
        }
        return rdr.getDefinitions();
    }

    private String getHtmlBegin() {
        return "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "<meta charset=\"UTF-8\">\n" +
            "<title>sampleFile - OpenGrok cross reference" +
            " for /sampleFile</title></head><body>\n";
    }

    private String getHtmlEnd() {
        return "</body>\n" +
            "</html>\n";
    }
}
