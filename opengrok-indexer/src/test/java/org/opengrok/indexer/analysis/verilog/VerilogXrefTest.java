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
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.verilog;

import org.junit.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.CtagsReader;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.Xrefer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;
import static org.opengrok.indexer.util.StreamUtils.copyStream;

/**
 * Tests the {@link VerilogXref} class.
 */
public class VerilogXrefTest {

    @Test
    public void sampleTest() throws IOException {
        writeAndCompare("analysis/verilog/sample.v",
                "analysis/verilog/sample_xref.html",
                getTagsDefinitions(), 81);
    }

    @Test
    public void shouldCloseTruncatedStringSpan() throws IOException {
        writeAndCompare("analysis/verilog/truncated.v",
            "analysis/verilog/truncated_xref.html",
            null, 1);
    }

    private void writeAndCompare(String sourceResource, String resultResource,
            Definitions defs, int expLOC) throws IOException {

        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();

        InputStream sourceRes = getClass().getClassLoader().getResourceAsStream(
                sourceResource);
        assertNotNull(sourceResource + " should get-as-stream", sourceRes);
        int actLOC = writeVerilogXref(new PrintStream(bytesOut), sourceRes, defs);
        sourceRes.close();

        InputStream resRes = getClass().getClassLoader().getResourceAsStream(
                resultResource);
        assertNotNull(resultResource + " should get-as-stream", resRes);
        byte[] expectedBytes = copyStream(resRes);
        resRes.close();
        bytesOut.close();

        String outStr = new String(bytesOut.toByteArray(), StandardCharsets.UTF_8);
        String[] gotten = outStr.split("\n");

        String expStr = new String(expectedBytes, StandardCharsets.UTF_8);
        String[] expected = expStr.split("\n");

        assertLinesEqual("Verilog xref", expected, gotten);
        assertEquals("Verilog LOC", expLOC, actLOC);
    }

    private int writeVerilogXref(PrintStream oss, InputStream iss,
             Definitions defs) throws IOException {

        oss.print(getHtmlBegin());

        Writer sw = new StringWriter();
        VerilogAnalyzerFactory fac = new VerilogAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        analyzer.setScopesEnabled(true);
        analyzer.setFoldingEnabled(true);
        WriteXrefArgs writeArgs = new WriteXrefArgs(
                new InputStreamReader(iss, StandardCharsets.UTF_8), sw);
        writeArgs.setDefs(defs);
        Xrefer xref = analyzer.writeXref(writeArgs);
        oss.print(sw.toString());

        oss.print(getHtmlEnd());
        return xref.getLOC();
    }

    private Definitions getTagsDefinitions() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/verilog/sampletags");
        assertNotNull("though sampletags should stream,", res);

        BufferedReader in = new BufferedReader(new InputStreamReader(
                res, StandardCharsets.UTF_8));

        CtagsReader rdr = new CtagsReader();
        String line;
        while ((line = in.readLine()) != null) {
            rdr.readLine(line);
        }
        return rdr.getDefinitions();
    }

    private static String getHtmlBegin() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<title>sampleFile - OpenGrok cross reference" +
                " for /sampleFile</title></head><body>\n";
    }

    private static String getHtmlEnd() {
        return "</body>\n" +
                "</html>\n";
    }
}
