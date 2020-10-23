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
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.haskell;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.CtagsReader;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.Xrefer;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;
import static org.opengrok.indexer.util.StreamUtils.copyStream;

/**
 * Tests the {@link HaskellXref} class.
 *
 * @author Harry Pan
 */
public class HaskellXrefTest {

    @Test
    public void basicTest() throws IOException {
        String s = "putStrLn \"Hello, world!\"";
        Writer w = new StringWriter();
        HaskellAnalyzerFactory fac = new HaskellAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs xargs = new WriteXrefArgs(new StringReader(s), w);
        Xrefer xref = analyzer.writeXref(xargs);
        assertLinesEqual("Haskell basicTest",
            "<a class=\"l\" name=\"1\" href=\"#1\">1</a>" +
            "<a href=\"/source/s?defs=putStrLn\" class=\"intelliWindow-symbol\"" +
            " data-definition-place=\"undefined-in-file\">putStrLn</a>" +
            " <span class=\"s\">&quot;Hello, world!&quot;</span>\n",
                w.toString());
        assertEquals("Haskell LOC", 1, xref.getLOC());
    }

    private static int writeHaskellXref(InputStream is, PrintStream os,
        Definitions defs) throws IOException {
        os.println("<!DOCTYPE html><html lang=\"en\"><head><meta http-equiv=\"content-type\" content=\"text/html;charset=UTF-8\" />"
                + "<link rel=\"stylesheet\" type=\"text/css\" "
                + "href=\"http://localhost:8080/source/default/style.css\" /><title>Haskell Xref Test</title></head>");
        os.println("<body><div id=\"src\"><pre>");
        Writer w = new StringWriter();
        HaskellAnalyzerFactory fac = new HaskellAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs args = new WriteXrefArgs(new InputStreamReader(is, StandardCharsets.UTF_8), w);
        args.setDefs(defs);
        Xrefer xref = analyzer.writeXref(args);
        os.print(w.toString());
        os.println("</pre></div></body></html>");
        return xref.getLOC();
    }

    @Test
    public void sampleTest() throws IOException {
        // load sample source
        InputStream sampleInputStream = getClass().getClassLoader().getResourceAsStream(
                "analysis/haskell/sample.hs");
        ByteArrayOutputStream sampleOutputStream = new ByteArrayOutputStream();

        Definitions defs = new Definitions();
        defs.addTag(6, "x'y'", "functions",
            "x'y' = let f' = 1; g'h = 2 in f' + g'h", 0, 0);
        int actLOC;
        try {
            actLOC = writeHaskellXref(sampleInputStream, new PrintStream(sampleOutputStream), defs);
        } finally {
            sampleInputStream.close();
            sampleOutputStream.close();
        }

        // load expected xref
        InputStream expectedInputStream = getClass().getClassLoader().getResourceAsStream(
                "analysis/haskell/sampleXrefExpected.html");
        ByteArrayOutputStream expectedOutputSteam = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int numBytesRead;
            do {
                numBytesRead = expectedInputStream.read(buffer, 0, buffer.length);
                if (numBytesRead > 0) {
                    expectedOutputSteam.write(buffer, 0, numBytesRead);
                }
            } while (numBytesRead >= 0);
        } finally {
            expectedInputStream.close();
            expectedOutputSteam.close();
        }

        String[] actual = new String(sampleOutputStream.toByteArray(), StandardCharsets.UTF_8).split("\\r?\\n");
        String[] expected = new String(expectedOutputSteam.toByteArray(), StandardCharsets.UTF_8).split("\\r?\\n");
        assertLinesEqual("Haskell sampleTest()", expected, actual);
        assertEquals("Haskell LOC", 3, actLOC);
    }

    @Test
    public void sampleTest2() throws IOException {
        writeAndCompare("analysis/haskell/sample2.hs",
            "analysis/haskell/sample2_xref.html",
            getTagsDefinitions(), 198);
    }

    @Test
    public void shouldCloseTruncatedStringSpan() throws IOException {
        writeAndCompare("analysis/haskell/truncated.hs",
            "analysis/haskell/truncated_xref.html",
            null, 1);
    }

    private void writeAndCompare(String sourceResource, String resultResource,
        Definitions defs, int expLOC) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        InputStream res = getClass().getClassLoader().getResourceAsStream(
            sourceResource);
        assertNotNull(sourceResource + " should get-as-stream", res);
        int actLOC = writeHaskellXref(res, new PrintStream(baos), defs);
        res.close();

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
            resultResource);
        assertNotNull(resultResource + " should get-as-stream", exp);
        byte[] expbytes = copyStream(exp);
        exp.close();
        baos.close();

        String ostr = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        String[] gotten = ostr.split("\\r?\\n");

        String estr = new String(expbytes, StandardCharsets.UTF_8);
        String[] expected = estr.split("\n");

        assertLinesEqual("Haskell xref", expected, gotten);
        assertEquals("Haskell LOC", expLOC, actLOC);
    }

    private Definitions getTagsDefinitions() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "analysis/haskell/sampletags");
        assertNotNull("though sampletags should stream,", res);

        BufferedReader in = new BufferedReader(new InputStreamReader(res, StandardCharsets.UTF_8));

        CtagsReader rdr = new CtagsReader();
        String line;
        while ((line = in.readLine()) != null) {
            rdr.readLine(line);
        }
        return rdr.getDefinitions();
    }
}
