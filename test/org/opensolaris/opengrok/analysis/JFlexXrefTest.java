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
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.lucene.document.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.c.CXref;
import org.opensolaris.opengrok.analysis.c.CxxXref;
import org.opensolaris.opengrok.analysis.csharp.CSharpXref;
import org.opensolaris.opengrok.analysis.document.TroffXref;
import org.opensolaris.opengrok.analysis.executables.JavaClassAnalyzerFactory;
import org.opensolaris.opengrok.analysis.fortran.FortranXref;
import org.opensolaris.opengrok.analysis.haskell.HaskellXref;
import org.opensolaris.opengrok.analysis.java.JavaXref;
import org.opensolaris.opengrok.analysis.lisp.LispXref;
import org.opensolaris.opengrok.analysis.perl.PerlXref;
import org.opensolaris.opengrok.analysis.plain.PlainXref;
import org.opensolaris.opengrok.analysis.plain.XMLXref;
import org.opensolaris.opengrok.analysis.scala.ScalaXref;
import org.opensolaris.opengrok.analysis.sh.ShXref;
import org.opensolaris.opengrok.analysis.sql.SQLXref;
import org.opensolaris.opengrok.analysis.tcl.TclXref;
import org.opensolaris.opengrok.analysis.uue.UuencodeXref;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.TestRepository;
import org.xml.sax.InputSource;

import static org.junit.Assert.*;

/**
 * Unit tests for JFlexXref.
 */
public class JFlexXrefTest {

    private static Ctags ctags;
    private static TestRepository repository;

    /**
     * This is what we expect to find at the beginning of the first line
     * returned by an xref.
     */
    private static final String FIRST_LINE_PREAMBLE =
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a>";

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
        bug15890LineCount(new ScalaXref(new StringReader(fileContents)));
        bug15890LineCount(new FortranXref(new StringReader(fileContents)));
        bug15890LineCount(new HaskellXref(new StringReader(fileContents)));
        bug15890LineCount(new XMLXref(new StringReader(fileContents)));
        bug15890LineCount(new ShXref(new StringReader(fileContents)));
        bug15890LineCount(new TclXref(new StringReader(fileContents)));
        bug15890LineCount(new SQLXref(new StringReader(fileContents)));
        bug15890LineCount(new TroffXref(new StringReader(fileContents)));
        bug15890LineCount(new PlainXref(new StringReader(fileContents)));
        bug15890LineCount(new PerlXref(new StringReader(fileContents)));
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
        bug15890Anchor(HaskellXref.class, "haskell/bug15890.hs");
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
        //TODO improve below to reflect all possible classes of a definition
        assertTrue(
                "No anchor found",
                out.toString().contains("\" name=\"bug15890\"/><a href="));
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

        assertEquals(FIRST_LINE_PREAMBLE + expectedOutput, output.toString());
    }

    /**
     * Regression test case for bug #16883. Some of the state used to survive
     * across invocations in ShXref, so that a syntax error in one file might
     * cause broken highlighting in subsequent files. Test that the instance
     * is properly reset now.
     */
    @Test
    public void bug16883() throws Exception {
        // Analyze a script with broken syntax (unterminated string literal)
        ShXref xref = new ShXref(new StringReader("echo \"xyz"));
        StringWriter out = new StringWriter();
        xref.write(out);
        assertEquals(
                FIRST_LINE_PREAMBLE +
                    "<b>echo</b> <span class=\"s\">\"xyz</span>",
                out.toString());

        // Reuse the xref and verify that the broken syntax in the previous
        // file doesn't cause broken highlighting in the next file
        out = new StringWriter();
        String contents = "echo \"hello\"";
        xref.reInit(contents.toCharArray(), contents.length());
        xref.write(out);
        assertEquals(
                FIRST_LINE_PREAMBLE +
                    "<b>echo</b> <span class=\"s\">\"hello\"</span>",
                out.toString());
    }

    /**
     * <p>
     * Test the handling of #include in C and C++. In particular, these issues
     * are tested:
     * </p>
     *
     * <ul>
     *
     * <li>
     * Verify that we use breadcrumb path for both #include &lt;x/y.h&gt; and
     * #include "x/y.h" in C and C++ (bug #17817)
     * </li>
     *
     * <li>
     * Verify that the link generated for #include &lt;vector&gt; performs a
     * path search (bug #17816)
     * </li>
     *
     * </ul>
     */
    @Test
    public void testCXrefInclude() throws Exception {
        testCXrefInclude(CXref.class);
        testCXrefInclude(CxxXref.class);
    }

    private void testCXrefInclude(Class<? extends JFlexXref> klass) throws Exception {
        String[][] testData = {
            {"#include <abc.h>", "#<b>include</b> &lt;<a href=\"/source/s?path=abc.h\">abc.h</a>&gt;"},
            {"#include <abc/def.h>", "#<b>include</b> &lt;<a href=\"/source/s?path=abc/\">abc</a>/<a href=\"/source/s?path=abc/def.h\">def.h</a>&gt;"},
            {"#include \"abc.h\"", "#<b>include</b> <span class=\"s\">\"<a href=\"/source/s?path=abc.h\">abc.h</a>\"</span>"},
            {"#include \"abc/def.h\"", "#<b>include</b> <span class=\"s\">\"<a href=\"/source/s?path=abc/\">abc</a>/<a href=\"/source/s?path=abc/def.h\">def.h</a>\"</span>"},
            {"#include <vector>", "#<b>include</b> &lt;<a href=\"/source/s?path=vector\">vector</a>&gt;"},
        };

        for (String[] s : testData) {
            StringReader in = new StringReader(s[0]);
            StringWriter out = new StringWriter();
            JFlexXref xref = klass.getConstructor(Reader.class).newInstance(in);
            xref.write(out);
            assertEquals(FIRST_LINE_PREAMBLE + s[1], out.toString());
        }
    }

    /**
     * Verify that template parameters are treated as class names rather than
     * filenames.
     */
    @Test
    public void testCxxXrefTemplateParameters() throws Exception {
        StringReader in = new StringReader("#include <vector>\nclass MyClass;\nstd::vector<MyClass> *v;");
        StringWriter out = new StringWriter();
        JFlexXref xref = new CxxXref(in);
        xref.write(out);
        assertTrue("Link to search for definition of class not found",
                   out.toString().contains("&lt;<a href=\"/source/s?defs=MyClass\""));
    }

    /**
     * Verify that ShXref handles here-documents. Bug #18198.
     */
    @Test
    public void testShXrefHeredoc() throws IOException {
        StringReader in = new StringReader(
                "cat<<EOF\n" +
                "This shouldn't cause any problem.\n" +
                "EOF\n" +
                "var='some string'\n");

        ShXref xref = new ShXref(in);
        StringWriter out = new StringWriter();
        xref.write(out);

        String[] result = out.toString().split("\n");

        // The single-quote on line 2 shouldn't start a string literal.
        assertTrue(result[1].endsWith("This shouldn't cause any problem."));

        // The string literal on line 4 should be recognized as one.
        assertTrue(
            result[3].endsWith("=<span class=\"s\">'some string'</span>"));
    }

    /**
     * Test that JavaXref handles empty Java comments. Bug #17885.
     */
    @Test
    public void testEmptyJavaComment() throws IOException {
        StringReader in = new StringReader("/**/\nclass xyz { }\n");
        JavaXref xref = new JavaXref(in);
        StringWriter out = new StringWriter();
        xref.write(out);
        // Verify that the comment's <span> block is terminated.
        assertTrue(out.toString().contains("<span class=\"c\">/**/</span>"));
    }

    @Test
    public void bug18586() throws IOException {
        String filename = repository.getSourceRoot() + "/sql/bug18586.sql";
        Reader in = new InputStreamReader(new FileInputStream(filename), "UTF-8");
        SQLXref xref = new SQLXref(in);
        xref.setDefs(ctags.doCtags(filename + "\n"));
        // The next call used to fail with an ArrayIndexOutOfBoundsException.
        xref.write(new StringWriter());
    }

    /**
     * Test that unterminated heredocs don't cause infinite loop in ShXref.
     * This originally became a problem after upgrade to JFlex 1.5.0.
     */
    @Test
    public void unterminatedHeredoc() throws IOException {
        ShXref xref = new ShXref(new StringReader(
                "cat << EOF\nunterminated heredoc"));

        StringWriter out = new StringWriter();

        // The next call used to loop forever.
        xref.write(out);

        assertEquals("<a class=\"l\" name=\"1\" href=\"#1\">1</a>"
            + "<a href=\"/source/s?defs=cat\" class=\"intelliWindow-symbol\" data-definition-place=\"undefined-in-file\">cat</a> &lt;&lt; EOF"
            + "<span class=\"s\">\n"
            + "<a class=\"l\" name=\"2\" href=\"#2\">2</a>"
            + "unterminated heredoc</span>",
            out.toString());
    }

    /**
     * Truncated uuencoded files used to cause infinite loops. Verify that
     * they work now.
     */
    @Test
    public void truncatedUuencodedFile() throws IOException {
        UuencodeXref xref = new UuencodeXref(
                new StringReader("begin 644 test.txt\n"));

        // Generating the xref used to loop forever.
        StringWriter out = new StringWriter();
        xref.write(out);

        assertEquals("<a class=\"l\" name=\"1\" href=\"#1\">1</a>"
                + "<strong>begin</strong> <i>644</i> "
                + "<a href=\"/source/s?q=test.txt\">test.txt</a>"
                + "<span class='c'>\n"
                + "<a class=\"l\" name=\"2\" href=\"#2\">2</a>",
                out.toString());
    }
    
    /**
     * Test that CSharpXref correctly handles verbatim strings that end with backslash
     */
    @Test
    public void testCsharpXrefVerbatimString() throws IOException {
        StringReader in = new StringReader("test(@\"\\some_windows_path_in_a_string\\\");");
        CSharpXref xref = new CSharpXref(in);
        StringWriter out = new StringWriter();
        xref.write(out);
        assertTrue(out.toString().contains("<span class=\"s\">@\"\\some_windows_path_in_a_string\\\"</span>"));
    }

    /**
     * Test that special characters in URLs are escaped in the xref.
     */
    @Test
    public void testEscapeLink() throws IOException {
        StringReader in = new StringReader("http://www.example.com/?a=b&c=d");
        PlainXref xref = new PlainXref(in);
        StringWriter out = new StringWriter();
        xref.write(out);
        assertTrue(out.toString().contains(
                "<a href=\"http://www.example.com/?a=b&amp;c=d\">" +
                "http://www.example.com/?a=b&amp;c=d</a>"));
    }

    /**
     * Test that JFlex rules that contain quotes don't cause invalid xref
     * to be produced.
     */
    @Test
    public void testJFlexRule() throws Exception {
        StringReader in = new StringReader("\\\" { yybegin(STRING); }");
        // JFlex files are usually analyzed with CAnalyzer.
        CXref xref = new CXref(in);
        StringWriter out = new StringWriter();
        xref.write(out);
        // Verify that the xref is well-formed XML. Used to throw
        // SAXParseException: The element type "span" must be terminated
        // by the matching end-tag "</span>".
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new InputSource(new StringReader("<doc>" + out + "</doc>")));
    }

    /**
     * Unterminated string literals or comments made CXref produce output
     * that was not valid XML, due to missing end tags. Test that it is no
     * longer so.
     */
    @Test
    public void testUnterminatedElements() throws Exception {
        for (String str : Arrays.asList("#define STR \"abc\n",
                                        "void f(); /* unterminated comment\n",
                                        "const char c = 'x\n")) {
            StringReader in = new StringReader(str);
            CXref xref = new CXref(in);
            StringWriter out = new StringWriter();
            xref.write(out);
            // Used to throw SAXParseException.
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new InputSource(new StringReader("<doc>" + out + "</doc>")));
        }
    }

    /**
     * Test that JavaClassAnalyzer produces well-formed output.
     */
    @Test
    public void testJavaClassAnalyzer() throws Exception {
        StreamSource src = new StreamSource() {
            @Override public InputStream getStream() throws IOException {
                final String path = "/" +
                    StringWriter.class.getName().replace('.', '/') +
                    ".class";
                return StringWriter.class.getResourceAsStream(path);
            }
        };
        Document doc = new Document();
        StringWriter out = new StringWriter();
        new JavaClassAnalyzerFactory().getAnalyzer().analyze(doc, src, out);
        // Used to throw SAXParseException.
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new InputSource(new StringReader("<doc>" + out + "</doc>")));
    }

    /**
     * Test that special characters in Fortran files are escaped.
     */
    @Test
    public void testFortranSpecialCharacters() throws Exception {
        FortranXref xref = new FortranXref(new StringReader("<?php?>"));
        StringWriter out = new StringWriter();
        xref.write(out);
        // Used to throw SAXParseException.
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            new InputSource(new StringReader("<doc>" + out + "</doc>")));
    }
}
