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
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.haskell;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import org.opensolaris.opengrok.analysis.CtagsReader;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.WriteXrefArgs;
import static org.opensolaris.opengrok.util.CustomAssertions.assertLinesEqual;

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
        FileAnalyzer analyzer = fac.getAnalyzer();
        analyzer.writeXref(new WriteXrefArgs(new StringReader(s), w));
        assertLinesEqual("Haskell basicTest",
            "<a class=\"l\" name=\"1\" href=\"#1\">1</a>" +
            "<a href=\"/source/s?defs=putStrLn\" class=\"intelliWindow-symbol\"" +
            " data-definition-place=\"undefined-in-file\">putStrLn</a>" +
            " <span class=\"s\">&quot;Hello, world!&quot;</span>\n",
                w.toString());
    }

    private static void writeHaskellXref(InputStream is, PrintStream os, Definitions defs) throws IOException {
        os.println(
                "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html;charset=UTF-8\" /><link rel=\"stylesheet\" type=\"text/css\" "
                + "href=\"http://localhost:8080/source/default/style.css\" /><title>Haskell Xref Test</title></head>");
        os.println("<body><div id=\"src\"><pre>");
        Writer w = new StringWriter();
        HaskellAnalyzerFactory fac = new HaskellAnalyzerFactory();
        FileAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs args = new WriteXrefArgs(
            new InputStreamReader(is, "UTF-8"), w);
        args.setDefs(defs);
        analyzer.writeXref(args);
        os.print(w.toString());
        os.println("</pre></div></body></html>");
    }

    @Test
    public void sampleTest() throws IOException {
        // load sample source
        InputStream sampleInputStream = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/haskell/sample.hs");
        ByteArrayOutputStream sampleOutputStream = new ByteArrayOutputStream();

        Definitions defs = new Definitions();
        defs.addTag(6, "x'y'", "functions", "x'y' = let f' = 1; g'h = 2 in f' + g'h");
        try {
            writeHaskellXref(sampleInputStream, new PrintStream(sampleOutputStream), defs);
        } finally {
            sampleInputStream.close();
            sampleOutputStream.close();
        }

        // load expected xref
        InputStream expectedInputStream = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/haskell/sampleXrefExpected.html");
        ByteArrayOutputStream expectedOutputSteam = new ByteArrayOutputStream();
        try {
            byte buffer[] = new byte[8192];
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

        String actual[] = new String(sampleOutputStream.toByteArray(), "UTF-8").split("\n");
        String expected[] = new String(expectedOutputSteam.toByteArray(), "UTF-8").split("\n");
        assertLinesEqual("Haskell sampleTest()", expected, actual);
    }

    @Test
    public void sampleTest2() throws IOException {
        writeAndCompare("org/opensolaris/opengrok/analysis/haskell/sample2.hs",
            "org/opensolaris/opengrok/analysis/haskell/sample2_xref.html",
            getTagsDefinitions());
    }

    @Test
    public void shouldCloseTruncatedStringSpan() throws IOException {
        writeAndCompare("org/opensolaris/opengrok/analysis/haskell/truncated.hs",
            "org/opensolaris/opengrok/analysis/haskell/truncated_xref.html",
            null);
    }

    private void writeAndCompare(String sourceResource, String resultResource,
        Definitions defs)
        throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream baosExp = new ByteArrayOutputStream();

        InputStream res = getClass().getClassLoader().getResourceAsStream(
            sourceResource);
        assertNotNull(sourceResource + " should get-as-stream", res);
        writeHaskellXref(res, new PrintStream(baos), defs);
        res.close();

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
            resultResource);
        assertNotNull(resultResource + " should get-as-stream", exp);
        copyStream(exp, baosExp);
        exp.close();
        baosExp.close();
        baos.close();

        String ostr = new String(baos.toByteArray(), "UTF-8");
        String gotten[] = ostr.split("\n");

        String estr = new String(baosExp.toByteArray(), "UTF-8");
        String expected[] = estr.split("\n");

        assertLinesEqual("Haskell xref", expected, gotten);
    }

    private void copyStream(InputStream iss, OutputStream oss)
        throws IOException {

        byte buffer[] = new byte[8192];
        int read;
        do {
            read = iss.read(buffer, 0, buffer.length);
            if (read > 0) {
                oss.write(buffer, 0, read);
            }
        } while (read >= 0);
    }

    private Definitions getTagsDefinitions() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "org/opensolaris/opengrok/analysis/haskell/sampletags");
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
}
