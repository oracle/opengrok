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

package org.opengrok.indexer.analysis.ada;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.opengrok.indexer.analysis.FileAnalyzer;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.Xrefer;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;
import static org.opengrok.indexer.util.StreamUtils.copyStream;

/**
 * Tests the {@link AdaXref} class.
 */
public class AdaXrefTest {

    @Test
    public void sampleTest() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "analysis/ada/sample.adb");
        assertNotNull("though sample.adb should stream,", res);
        int actLOC = writeAdaXref(res, new PrintStream(baos));
        res.close();

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
            "analysis/ada/ada_xrefres.html");
        assertNotNull("ada_xrefres.html should stream,", exp);
        byte[] expbytes = copyStream(exp);
        exp.close();
        baos.close();

        String ostr = new String(baos.toByteArray(), "UTF-8");
        String estr = new String(expbytes, "UTF-8");
        assertLinesEqual("Ada xref", estr, ostr);
        assertEquals("Ada LOC", 19, actLOC);
    }

    private int writeAdaXref(InputStream iss, PrintStream oss)
            throws IOException {
        oss.print(getHtmlBegin());

        Writer sw = new StringWriter();
        AdaAnalyzerFactory fac = new AdaAnalyzerFactory();
        FileAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs wargs = new WriteXrefArgs(
            new InputStreamReader(iss, "UTF-8"), sw);
        Xrefer xref = analyzer.writeXref(wargs);

        oss.print(sw.toString());
        oss.print(getHtmlEnd());
        return xref.getLOC();
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
