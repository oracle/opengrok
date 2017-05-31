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
 */
package org.opensolaris.opengrok.analysis.php;

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
        PhpAnalyzer.writeXref(new StringReader(s), w, null, null, null);
        assertEquals(
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a><strong>&lt;?php</strong> <a href=\"/"
                + "source/s?defs=foo\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">foo</a> <a href=\"/source/s?defs=bar\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">bar</a>",
                w.toString());
    }

    @Test
    public void basicSingleQuotedStringTest() throws IOException {
        String s = "<?php define(\"FOO\", 'BAR\\'\"'); $foo='bar'; $hola=\"ls\"; $hola=''; $hola=\"\";";
        Writer w = new StringWriter();
        PhpAnalyzer.writeXref(new StringReader(s), w, null, null, null);
        assertEquals(
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a><strong>&lt;?php</strong> "
                + "<a href=\"/source/s?defs=define\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">define</a>(<span class=\"s\">\"FOO\"</span>, <span class=\"s\">'BAR<strong>\\'</strong>\"'</span>); "
                + "$<a href=\"/source/s?defs=foo\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">foo</a>=<span class=\"s\">'bar'</span>; "
                + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">\"ls\"</span>; "
                + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">''</span>; "
                + "$<a href=\"/source/s?defs=hola\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">hola</a>=<span class=\"s\">\"\"</span>;",
                w.toString());
    }


    public static void writePhpXref(InputStream is, PrintStream os) throws IOException {
        os.println(
                "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html;charset=UTF-8\" /><link rel=\"stylesheet\" type=\"text/css\" "
                + "href=\"http://localhost:8080/source/default/style.css\" /><title>PHP Xref Test</title></head>");
        os.println("<body><div id=\"src\"><pre>");
        Writer w = new StringWriter();
        PhpAnalyzer.writeXref(
                new InputStreamReader(is, "UTF-8"),
                w, null, null, null);
        os.print(w.toString());
        os.println("</pre></div></body></html>");
    }

    public static void main(String args[]) throws IOException {
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
        ByteArrayOutputStream baosExp = new ByteArrayOutputStream();

        try {
            writePhpXref(res, new PrintStream(baos));
        } finally {
            res.close();
        }

        InputStream exp = getClass().getClassLoader().getResourceAsStream(
                "org/opensolaris/opengrok/analysis/php/sampleXrefRes.html");

        try {
            byte buffer[] = new byte[8192];
            int read;
            do {
                read = exp.read(buffer, 0, buffer.length);
                if (read > 0) {
                    baosExp.write(buffer, 0, read);
                }
            } while (read >= 0);
        } finally {
            baosExp.close();
        }

        String gotten[] = new String(baos.toByteArray(), "UTF-8").split("\n");
        String expected[] = new String(baosExp.toByteArray(), "UTF-8").split("\n");

        assertEquals(expected.length, gotten.length);

        for (int i = 0; i < gotten.length; i++) {
            assertEquals(gotten[i].trim(), expected[i].trim());
        }
    }
}
