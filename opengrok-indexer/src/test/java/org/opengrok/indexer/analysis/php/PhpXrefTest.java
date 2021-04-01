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
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.php;

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
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.CtagsReader;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.Xrefer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;
import static org.opengrok.indexer.util.StreamUtils.copyStream;

/**
 * Tests the {@link PhpXref} class.
 * @author Gustavo Lopes
 */
public class PhpXrefTest {

    @Test
    public void basicTest() throws IOException {
        String s = "<?php foo bar";
        Writer w = new StringWriter();
        PhpAnalyzerFactory fac = new PhpAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs xargs = new WriteXrefArgs(new StringReader(s), w);
        Xrefer xref = analyzer.writeXref(xargs);
        assertEquals(
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a><strong>&lt;?php</strong> <a href=\"/"
                        + "source/s?defs=foo\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">foo</a>" +
                        " <a href=\"/source/s?defs=bar\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">bar</a>",
                w.toString());
        assertEquals(1, xref.getLOC(), "PHP LOC");
    }

    @Test
    public void basicSingleQuotedStringTest() throws IOException {
        String s = "<?php define(\"FOO\", 'BAR\\'\"'); $foo='bar'; $hola=\"ls\"; $hola=''; $hola=\"\";";
        Writer w = new StringWriter();
        PhpAnalyzerFactory fac = new PhpAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs xargs = new WriteXrefArgs(new StringReader(s), w);
        Xrefer xref = analyzer.writeXref(xargs);
        assertLinesEqual("PHP quoting",
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a><strong>&lt;?php</strong> "
                        + "<a href=\"/source/s?defs=define\" class=\"intelliWindow-symbol\" "
                        + "data-definition-place=\"undefined-in-file\">define</a>(<span class=\"s\">&quot;FOO&quot;</span>,"
                        + " <span class=\"s\">&apos;BAR<strong>\\&apos;</strong>&quot;&apos;</span>); "
                        + "$<a href=\"/source/s?defs=foo\" class=\"intelliWindow-symbol\" "
                        + "data-definition-place=\"undefined-in-file\">foo</a>=<span class=\"s\">&apos;bar&apos;</span>; "
                        + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" "
                        + "data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">&quot;ls&quot;</span>; "
                        + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" "
                        + "data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">&apos;&apos;</span>; "
                        + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" "
                        + "data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">&quot;&quot;</span>;",
                w.toString());
        assertEquals(1, xref.getLOC(), "PHP LOC");
    }


    public int writePhpXref(InputStream is, PrintStream os) throws IOException {
        os.println(
                "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html;charset=UTF-8\" />"
                        + "<link rel=\"stylesheet\" type=\"text/css\" "
                        + "href=\"http://localhost:8080/source/default/style.css\" /><title>PHP Xref Test</title></head>");
        os.println("<body><div id=\"src\"><pre>");
        Writer w = new StringWriter();
        PhpAnalyzerFactory fac = new PhpAnalyzerFactory();
        AbstractAnalyzer analyzer = fac.getAnalyzer();
        WriteXrefArgs wargs = new WriteXrefArgs(new InputStreamReader(is, StandardCharsets.UTF_8), w);
        wargs.setDefs(getTagsDefinitions());
        analyzer.setScopesEnabled(true);
        analyzer.setFoldingEnabled(true);
        Xrefer xref = analyzer.writeXref(wargs);
        os.print(w.toString());
        os.println("</pre></div></body></html>");
        return xref.getLOC();
    }

    public void main(String[] args) throws IOException {
        InputStream is;
        if (args.length == 0) {
            is = PhpXrefTest.class.getClassLoader().getResourceAsStream(
                    "analysis/php/sample.php");
        } else {
            is = new FileInputStream(new File(args[0]));
        }

        writePhpXref(is, System.out);
    }

    @Test
    public void sampleTest() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/php/sample.php");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int actLOC;
        try {
            actLOC = writePhpXref(res, new PrintStream(baos));
        } finally {
            res.close();
        }

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
                "analysis/php/sampleXrefRes.html");
        byte[] expbytes = copyStream(exp);

        String[] gotten = new String(baos.toByteArray(), StandardCharsets.UTF_8).split("\\r?\\n");
        String[] expected = new String(expbytes, StandardCharsets.UTF_8).split("\n");
        assertLinesEqual("PHP xref", expected, gotten);
        assertEquals(29, actLOC, "PHP LOC");

        assertEquals(expected.length, gotten.length);
    }

    private Definitions getTagsDefinitions() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream("analysis/php/sampletags");
        assertNotNull(res, "though sampletags should stream,");

        BufferedReader in = new BufferedReader(new InputStreamReader(res, StandardCharsets.UTF_8));

        CtagsReader rdr = new CtagsReader();
        String line;
        while ((line = in.readLine()) != null) {
            rdr.readLine(line);
        }
        return rdr.getDefinitions();
    }
}
