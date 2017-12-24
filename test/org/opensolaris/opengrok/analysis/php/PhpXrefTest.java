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
package org.opensolaris.opengrok.analysis.php;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import org.opensolaris.opengrok.analysis.CtagsReader;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.WriteXrefArgs;
import static org.opensolaris.opengrok.util.CustomAssertions.assertLinesEqual;
import static org.opensolaris.opengrok.util.StreamUtils.copyStream;

/**
 * Tests the {@link PhpXref} class.
 *
 * @author Gustavo Lopes
 */
public class PhpXrefTest {

    @Test
    public void basicTest() throws IOException {
        String s = "<?php foo bar";
        Writer w = new StringWriter();
        PhpAnalyzerFactory fac = new PhpAnalyzerFactory();
        FileAnalyzer analyzer = fac.getAnalyzer();
        analyzer.writeXref(new WriteXrefArgs(new StringReader(s), w));
        assertEquals(
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a><strong>&lt;?php</strong> <a href=\"/"
                + "source/s?defs=foo\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">foo</a> <a href=\"/source/s?defs=bar\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">bar</a>",
                w.toString());
    }

    @Test
    public void basicSingleQuotedStringTest() throws IOException {
        String s = "<?php define(\"FOO\", 'BAR\\'\"'); $foo='bar'; $hola=\"ls\"; $hola=''; $hola=\"\";";
        Writer w = new StringWriter();
        PhpAnalyzerFactory fac = new PhpAnalyzerFactory();
        FileAnalyzer analyzer = fac.getAnalyzer();
        analyzer.writeXref(new WriteXrefArgs(new StringReader(s), w));
        assertLinesEqual("PHP quoting",
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a><strong>&lt;?php</strong> "
                + "<a href=\"/source/s?defs=define\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">define</a>(<span class=\"s\">&quot;FOO&quot;</span>, <span class=\"s\">&apos;BAR<strong>\\&apos;</strong>&quot;&apos;</span>); "
                + "$<a href=\"/source/s?defs=foo\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">foo</a>=<span class=\"s\">&apos;bar&apos;</span>; "
                + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">&quot;ls&quot;</span>; "
                + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">&apos;&apos;</span>; "
                + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">&quot;&quot;</span>;",
                w.toString());
    }


    public void writePhpXref(InputStream is, PrintStream os) throws IOException {
        os.println(
                "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html;charset=UTF-8\" /><link rel=\"stylesheet\" type=\"text/css\" "
                + "href=\"http://localhost:8080/source/default/style.css\" /><title>PHP Xref Test</title></head>");
        os.println("<body><div id=\"src\"><pre>");
        Writer w = new StringWriter();
        PhpAnalyzerFactory fac = new PhpAnalyzerFactory();
        FileAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs wargs = new WriteXrefArgs(
            new InputStreamReader(is, "UTF-8"), w);
        wargs.setDefs(getTagsDefinitions());
        analyzer.setScopesEnabled(true);
        analyzer.setFoldingEnabled(true);
        analyzer.writeXref(wargs);
        os.print(w.toString());
        os.println("</pre></div></body></html>");
    }

    public void main(String args[]) throws IOException {
        InputStream is = null;
        if (args.length == 0) {
            is = PhpXrefTest.class.getClassLoader().getResourceAsStream(
                    "org/opensolaris/opengrok/analysis/php/sample.php");
        } else {
            is = new FileInputStream(new File(args[0]));
        }

        writePhpXref(is, System.out);
    }

    @Test
    public void sampleTest() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/php/sample.php");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            writePhpXref(res, new PrintStream(baos));
        } finally {
            res.close();
        }

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/php/sampleXrefRes.html");
        byte[] expbytes = copyStream(exp);

        String gotten[] = new String(baos.toByteArray(), "UTF-8").split("\n");
        String expected[] = new String(expbytes, "UTF-8").split("\n");
        assertLinesEqual("PHP xref", expected, gotten);

        assertEquals(expected.length, gotten.length);
    }

    private Definitions getTagsDefinitions() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "org/opensolaris/opengrok/analysis/php/sampletags");
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
