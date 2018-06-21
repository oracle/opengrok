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

package org.opengrok.indexer.analysis.perl;

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
 * Tests the {@link PerlXref} class.
 */
public class PerlXrefTest {

    @Test
    public void sampleTest() throws IOException {
        writeAndCompare("analysis/perl/sample.pl",
            "analysis/perl/samplexrefres.html", 258);
    }

    @Test
    public void shouldCloseTruncateStringSpan() throws IOException {
        writeAndCompare("analysis/perl/truncated.pl",
            "analysis/perl/truncated_xrefres.html",
            1);
    }

    private void writeAndCompare(String sourceResource, String resultResource,
        int expLOC) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        InputStream res = getClass().getClassLoader().getResourceAsStream(
            sourceResource);
        assertNotNull(sourceResource + " should get-as-stream", res);
        int actLOC = writePerlXref(res, new PrintStream(baos));
        res.close();

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
            resultResource);
        assertNotNull(resultResource + " should get-as-stream", exp);
        byte[] expbytes = copyStream(exp);
        exp.close();
        baos.close();

        String ostr = new String(baos.toByteArray(), "UTF-8");
        String estr = new String(expbytes, "UTF-8");
        assertLinesEqual("Perl xref", estr, ostr);
        assertEquals("Perl LOC", expLOC, actLOC);
    }

    private int writePerlXref(InputStream iss, PrintStream oss)
        throws IOException {

        oss.print(getHtmlBegin());

        Writer sw = new StringWriter();
        PerlAnalyzerFactory fac = new PerlAnalyzerFactory();
        FileAnalyzer analyzer = fac.getAnalyzer();
        Xrefer xref = analyzer.writeXref(new WriteXrefArgs(
            new InputStreamReader(iss, "UTF-8"), sw));
        oss.print(sw.toString());

        oss.print(getHtmlEnd());
        return xref.getLOC();
    }

    private static String getHtmlBegin() {
        return "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "<meta charset=\"UTF-8\">\n" +
            "<title>sampleTest.pl - OpenGrok cross reference" +
            " for /sampleTest.pl</title></head><body>\n";
    }

    private static String getHtmlEnd() {
        return "</body>\n" +
            "</html>\n";
    }
}
