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
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.c.CXref;
import org.opensolaris.opengrok.analysis.c.CxxXref;
import org.opensolaris.opengrok.analysis.document.TroffXref;
import org.opensolaris.opengrok.analysis.fortran.FortranXref;
import org.opensolaris.opengrok.analysis.java.JavaXref;
import org.opensolaris.opengrok.analysis.lisp.LispXref;
import org.opensolaris.opengrok.analysis.plain.PlainXref;
import org.opensolaris.opengrok.analysis.plain.XMLXref;
import org.opensolaris.opengrok.analysis.sh.ShXref;
import org.opensolaris.opengrok.analysis.sql.SQLXref;
import org.opensolaris.opengrok.analysis.tcl.TclXref;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.TestRepository;

import static org.junit.Assert.*;

/**
 * Unit tests for JFlexXref.
 */
public class JFlexXrefTest {

    private static Ctags ctags;
    private static TestRepository repository;

    @BeforeClass
    public static void setUpClass() throws Exception {
        ctags = new Ctags();
        ctags.setBinary(RuntimeEnvironment.getInstance().getCtags());
        repository = new TestRepository();
        repository.create(JFlexXrefTest.class.getResourceAsStream(
                "/org/opensolaris/opengrok/index/source.zip"));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        ctags.close();
        ctags = null;
        repository.destroy();
    }

    /**
     * Regression test case for bug #15890. Check that we get the expected the
     * expected line count from input with some special characters that used
     * to cause trouble.
     */
    @Test
    public void testBug15890LineCount() throws Exception {
        String fileContents =
                "line 1\n" +
                "line 2\n" +
                "line 3\n" +
                "line 4 with \u000B char\n" +
                "line 5 with \u000C char\n" +
                "line 6 with \u0085 char\n" +
                "line 7 with \u2028 char\n" +
                "line 8 with \u2029 char\n" +
                "line 9\n";

        bug15890LineCount(new CXref(new StringReader(fileContents)));
        bug15890LineCount(new CxxXref(new StringReader(fileContents)));
        bug15890LineCount(new LispXref(new StringReader(fileContents)));
        bug15890LineCount(new JavaXref(new StringReader(fileContents)));
        bug15890LineCount(new FortranXref(new StringReader(fileContents)));
        bug15890LineCount(new XMLXref(new StringReader(fileContents)));
        bug15890LineCount(new ShXref(new StringReader(fileContents)));
        bug15890LineCount(new TclXref(new StringReader(fileContents)));
        bug15890LineCount(new SQLXref(new StringReader(fileContents)));
        bug15890LineCount(new TroffXref(new StringReader(fileContents)));
        bug15890LineCount(new PlainXref(new StringReader(fileContents)));
    }

    /**
     * Helper method that checks the line count for
     * {@link #testBug15890LineCount()}.
     *
     * @param xref an instance of the xref class to test
     */
    private void bug15890LineCount(JFlexXref xref) throws Exception {
        xref.write(new StringWriter());
        assertEquals(10, xref.getLineNumber());
    }

    /**
     * Regression test case for bug #15890. Check that an anchor is correctly
     * inserted for definitions that appear after some special characters that
     * used to cause trouble.
     */
    @Test
    public void testBug15890Anchor() throws Exception {
        bug15890Anchor(CXref.class, "c/bug15890.c");
        bug15890Anchor(CxxXref.class, "c/bug15890.c");
        bug15890Anchor(LispXref.class, "lisp/bug15890.lisp");
        bug15890Anchor(JavaXref.class, "java/bug15890.java");
    }

    /**
     * Helper method for {@link #testBug15890Anchor()}.
     *
     * @param klass the Xref sub-class to test
     * @param path path to input file with a definition
     */
    private void bug15890Anchor(Class<? extends JFlexXref> klass, String path)
            throws Exception {
        File file = new File(repository.getSourceRoot() + File.separator + path);
        Definitions defs = ctags.doCtags(file.getAbsolutePath() + "\n");

        // Input files contain non-ascii characters and are encoded in UTF-8
        Reader in = new InputStreamReader(new FileInputStream(file), "UTF-8");

        JFlexXref xref = klass.getConstructor(Reader.class).newInstance(in);
        xref.setDefs(defs);

        StringWriter out = new StringWriter();
        xref.write(out);
        assertTrue(
                "No anchor found",
                out.toString().contains("<a class=\"d\" name=\"bug15890\"/>"));
    }

    /**
     * Regression test case for bug #14663, which used to break syntax
     * highlighting in ShXref.
     */
    @Test
    public void testBug14663() throws Exception {
        // \" should not start a new string literal
        assertXrefLine(ShXref.class, "echo \\\"", "<b>echo</b> \\\"");
        // \" should not terminate a string literal
        assertXrefLine(ShXref.class, "echo \"\\\"\"",
                "<b>echo</b> <span class=\"s\">\"\\\"\"</span>");
        // \` should not start a command substitution
        assertXrefLine(ShXref.class, "echo \\`", "<b>echo</b> \\`");
        // \` should not start command substitution inside a string
        assertXrefLine(ShXref.class, "echo \"\\`\"",
                "<b>echo</b> <span class=\"s\">\"\\`\"</span>");
        // \` should not terminate command substitution
        assertXrefLine(ShXref.class, "echo `\\``",
                "<b>echo</b> <span>`\\``</span>");
        // $# should not start a comment
        assertXrefLine(ShXref.class, "$#", "$#");
    }

    /**
     * Helper method that checks that the expected output is produced for a
     * line with the specified xref class. Fails if the output is not as
     * expected.
     *
     * @param xrefClass xref class to test
     * @param inputLine the source code line to parse
     * @param expectedOutput the expected output from the xreffer
     */
    private void assertXrefLine(Class<? extends JFlexXref> xrefClass,
            String inputLine, String expectedOutput) throws Exception {
        JFlexXref xref = xrefClass.getConstructor(Reader.class).newInstance(
                new StringReader(inputLine));

        StringWriter output = new StringWriter();
        xref.write(output);

        // This prefix is always prepended to the first line:
        String prefix = "<a class=\"l\" name=\"1\" href=\"#1\">"
                + "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1&nbsp;</a>";

        assertEquals(prefix + expectedOutput, output.toString());
    }
}
