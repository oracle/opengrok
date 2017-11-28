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
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.c;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.opensolaris.opengrok.analysis.CtagsReader;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.WriteXrefArgs;
import static org.opensolaris.opengrok.util.CustomAssertions.assertLinesEqual;

/**
 * Tests the {@link CXref} class.
 */
public class CXrefTest {

    @Test
    public void sampleTest() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream baosExp = new ByteArrayOutputStream();

        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "org/opensolaris/opengrok/analysis/c/sample.c");
        assertNotNull("though sample.c should stream,", res);
        writeCXref(res, new PrintStream(baos));
        res.close();

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
            "org/opensolaris/opengrok/analysis/c/c_xrefres.html");
        assertNotNull("c_xrefres.html should stream,", exp);
        copyStream(exp, baosExp);
        exp.close();
        baosExp.close();
        baos.close();

        String ostr = new String(baos.toByteArray(), "UTF-8");
        String estr = new String(baosExp.toByteArray(), "UTF-8");
        assertLinesEqual("C xref", estr, ostr);
    }

    private void writeCXref(InputStream iss, PrintStream oss)
        throws IOException {

        oss.print(getHtmlBegin());

        Writer sw = new StringWriter();
        CAnalyzerFactory fac = new CAnalyzerFactory();
        FileAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs wargs = new WriteXrefArgs(
            new InputStreamReader(iss, "UTF-8"), sw);
        wargs.setDefs(getTagsDefinitions());
        analyzer.setScopesEnabled(true);
        analyzer.setFoldingEnabled(true);
        analyzer.writeXref(wargs);

        oss.print(sw.toString());
        oss.print(getHtmlEnd());
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
            "org/opensolaris/opengrok/analysis/c/sampletags_c");
        assertNotNull("though sampletags_c should stream,", res);

        BufferedReader in = new BufferedReader(new InputStreamReader(
            res, "UTF-8"));

        CtagsReader rdr = new CtagsReader();
        String line;
        while ((line = in.readLine()) != null) {
            rdr.readLine(line);
        }
        return rdr.getDefinitions();
    }

    private static String getHtmlBegin() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n" +
            "    \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"" +
            " xml:lang=\"en\" lang=\"en\"\n" +
            "      class=\"xref\">\n" +
            "<head>\n" +
            "<title>sampleFile - OpenGrok cross reference" +
            " for /sampleFile</title></head><body>\n";
    }

    private static String getHtmlEnd() {
        return "</body>\n" +
            "</html>\n";
    }
}
