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
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis.haskell;

import java.io.*;
import static org.junit.Assert.*;
import org.junit.Test;

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
        HaskellAnalyzer.writeXref(new StringReader(s), w, null, null, null);
        assertEquals(
            "<a class=\"l\" name=\"1\" href=\"#1\">1</a>" +
            "<a href=\"/source/s?defs=putStrLn\" onmouseover=\"onMouseOverSymbol('putStrLn', 'undefined-in-file')\">putStrLn</a>" +
            " <span class=\"s\">\"&#72;&#101;&#108;&#108;&#111;&#44; &#119;&#111;&#114;&#108;&#100;&#33;\"</span>",
            w.toString());
    }

    private static void writeHaskellXref(InputStream is, PrintStream os) throws IOException {
        os.println(
            "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html;charset=UTF-8\" /><link rel=\"stylesheet\" type=\"text/css\" "
            + "href=\"http://localhost:8080/source/default/style.css\" /><title>Haskell Xref Test</title></head>");
        os.println("<body><div id=\"src\"><pre>");
        Writer w = new StringWriter();
        HaskellAnalyzer.writeXref(new InputStreamReader(is, "UTF-8"), w, null, null, null);
        os.print(w.toString());
        os.println("</pre></div></body></html>");
    }

    @Test
    public void sampleTest() throws IOException {
        // load sample source
        InputStream sampleInputStream = getClass().getClassLoader().getResourceAsStream(
            "org/opensolaris/opengrok/analysis/haskell/sample.hs");
        ByteArrayOutputStream sampleOutputStream = new ByteArrayOutputStream();

        try {
            writeHaskellXref(sampleInputStream, new PrintStream(sampleOutputStream));
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
        assertArrayEquals(expected, actual);
    }
}
