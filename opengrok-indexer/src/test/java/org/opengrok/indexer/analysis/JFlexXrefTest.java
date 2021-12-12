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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2021, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.lucene.document.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.c.CXref;
import org.opengrok.indexer.analysis.c.CxxXref;
import org.opengrok.indexer.analysis.csharp.CSharpXref;
import org.opengrok.indexer.analysis.document.TroffXref;
import org.opengrok.indexer.analysis.executables.JavaClassAnalyzerFactory;
import org.opengrok.indexer.analysis.fortran.FortranXref;
import org.opengrok.indexer.analysis.haskell.HaskellXref;
import org.opengrok.indexer.analysis.java.JavaXref;
import org.opengrok.indexer.analysis.lisp.LispXref;
import org.opengrok.indexer.analysis.perl.PerlXref;
import org.opengrok.indexer.analysis.plain.PlainXref;
import org.opengrok.indexer.analysis.plain.XMLXref;
import org.opengrok.indexer.analysis.scala.ScalaXref;
import org.opengrok.indexer.analysis.sh.ShXref;
import org.opengrok.indexer.analysis.sql.SQLXref;
import org.opengrok.indexer.analysis.tcl.TclXref;
import org.opengrok.indexer.analysis.uue.UuencodeXref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;
import org.opengrok.indexer.util.TestRepository;
import org.xml.sax.InputSource;

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

    @BeforeAll
    public static void setUpClass() throws Exception {
        ctags = new Ctags();
        repository = new TestRepository();
        repository.create(JFlexXrefTest.class.getClassLoader().getResource("sources"));
    }

    @AfterAll
    public static void tearDownClass() {
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
    void testBug15890LineCount() throws Exception {
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

        bug15890LineCount(new JFlexXref(new CXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new CxxXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new LispXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new JavaXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new ScalaXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new FortranXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new HaskellXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new XMLXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new ShXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new TclXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new SQLXref(new StringReader(fileContents))));
        bug15890LineCount(new TroffXref(new StringReader(fileContents)));
        bug15890LineCount(new JFlexXref(new PlainXref(new StringReader(fileContents))));
        bug15890LineCount(new JFlexXref(new PerlXref(new StringReader(fileContents))));
    }

    /**
     * Helper method that checks the line count for
     * {@link #testBug15890LineCount()}.
     *
     * @param xref an instance of the xref class to test
     */
    private void bug15890LineCount(JFlexXref xref) throws Exception {
        final int EXP_N = 10;
        StringWriter out = new StringWriter();
        xref.write(out);
        if (EXP_N != xref.getLineNumber()) {
            System.out.println(out.toString());
            assertEquals(EXP_N, xref.getLineNumber(), "xref line count");
        }
    }

    /**
     * Helper method that checks the line count for
     * {@link #testBug15890LineCount()}.
     *
     * @param xref an instance of the xref class to test
     */
    private void bug15890LineCount(JFlexNonXref xref) throws Exception {
        xref.write(new StringWriter());
        assertEquals(10, xref.getLineNumber());
    }

    /**
     * Regression test case for bug #15890. Check that an anchor is correctly
     * inserted for definitions that appear after some special characters that
     * used to cause trouble.
     */
    @Test
    void testBug15890Anchor() throws Exception {
        bug15890Anchor(CXref.class, "c/bug15890.c");
        bug15890Anchor(CxxXref.class, "c/bug15890.c");
        bug15890Anchor(HaskellXref.class, "haskell/bug15890.hs");
        bug15890Anchor(LispXref.class, "lisp/bug15890.lisp");
        bug15890Anchor(JavaXref.class, "java/bug15890.java");
    }

    /**
     * Helper method for {@link #testBug15890Anchor()}.
     *
     * @param klass the Xref subclass to test
     * @param path path to input file with a definition
     */
    private void bug15890Anchor(Class<? extends JFlexSymbolMatcher> klass, String path) throws Exception {
        File file = new File(repository.getSourceRoot() + File.separator + path);
        Definitions defs = ctags.doCtags(file.getAbsolutePath());

        // Input files contain non-ascii characters and are encoded in UTF-8
        try (Reader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {

            JFlexXref xref = new JFlexXref(klass.getConstructor(Reader.class).newInstance(in));
            xref.setDefs(defs);

            StringWriter out = new StringWriter();
            xref.write(out);
            String outstr = out.toString();
            boolean hasAnchor = outstr.contains("\" name=\"bug15890\"/><a href=");
            assertTrue(hasAnchor, "No bug15890 anchor found for " + path + ":\n" + outstr);
        }
    }

    /**
     * Regression test case for bug #14663, which used to break syntax
     * highlighting in ShXref.
     */
    @Test
    void testBug14663() throws Exception {
        // \" should not start a new string literal
        assertXrefLine(ShXref.class, "echo \\\"", "<b>echo</b> \\&quot;");
        // \" should not terminate a string literal
        assertXrefLine(ShXref.class, "echo \"\\\"\"",
                "<b>echo</b> <span class=\"s\">&quot;\\&quot;&quot;</span>");
        // \` should not start a command substitution
        assertXrefLine(ShXref.class, "echo \\`", "<b>echo</b> \\`");
        // \` should not start command substitution inside a string
        assertXrefLine(ShXref.class, "echo \"\\`\"",
                "<b>echo</b> <span class=\"s\">&quot;\\`&quot;</span>");
        // \` should not terminate command substitution
        assertXrefLine(ShXref.class, "echo `\\``",
                "<b>echo</b> `\\``");
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
    private void assertXrefLine(Class<? extends JFlexSymbolMatcher> xrefClass,
            String inputLine, String expectedOutput) throws Exception {
        JFlexXref xref = new JFlexXref(xrefClass.getConstructor(Reader.class).
            newInstance(new StringReader(inputLine)));

        StringWriter output = new StringWriter();
        xref.write(output);

        assertLinesEqual("xref " + xrefClass.getSimpleName(),
            FIRST_LINE_PREAMBLE + expectedOutput, output.toString());
    }

    /**
     * Regression test case for bug #16883. Some of the state used to survive
     * across invocations in ShXref, so that a syntax error in one file might
     * cause broken highlighting in subsequent files. Test that the instance
     * is properly reset now.
     */
    @Test
    void bug16883() throws Exception {
        final String ECHO_QUOT_XYZ = "echo \"xyz";
        // Analyze a script with broken syntax (unterminated string literal)
        JFlexXref xref = new JFlexXref(new ShXref(
            new StringReader(ECHO_QUOT_XYZ)));
        StringWriter out = new StringWriter();
        xref.write(out);
        assertLinesEqual("Unterminated string:\n" + ECHO_QUOT_XYZ,
            FIRST_LINE_PREAMBLE +
            "<b>echo</b> <span class=\"s\">&quot;xyz</span>", out.toString());

        // Reuse the xref and verify that the broken syntax in the previous
        // file doesn't cause broken highlighting in the next file
        out = new StringWriter();
        String contents = "echo \"hello\"";
        xref.setReader(new StringReader(new String(contents.toCharArray())));
        xref.reset();
        xref.write(out);
        assertLinesEqual("reused ShXref after broken syntax",
            FIRST_LINE_PREAMBLE +
            "<b>echo</b> <span class=\"s\">&quot;hello&quot;</span>",
            out.toString());
    }

    /**
     * Test the handling of #include in C and C++. In particular, these issues
     * are tested:
     * <p>
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
    void testCXrefInclude() throws Exception {
        testCXrefInclude(CXref.class);
    }

    @Test
    void testCxxXrefInclude() throws Exception {
        testCXrefInclude(CxxXref.class);
    }

    private void testCXrefInclude(Class<? extends JFlexSymbolMatcher> klass) throws Exception {
        String[][] testData = {
            {"#include <abc.h>", "#<b>include</b> &lt;<a href=\"/source/s?path=abc.h\">abc.h</a>&gt;"},
            {"#include <abc/def.h>", "#<b>include</b> &lt;<a href=\"/source/s?path=abc/\">abc</a>/" +
                    "<a href=\"/source/s?path=abc/def.h\">def.h</a>&gt;"},
            {"#include \"abc.h\"", "#<b>include</b> &quot;<a href=\"/source/s?path=abc.h\">abc.h</a>&quot;"},
            {"#include \"abc/def.h\"", "#<b>include</b> &quot;<a href=\"/source/s?path=abc/\">abc</a>/" +
                    "<a href=\"/source/s?path=abc/def.h\">def.h</a>&quot;"},
            {"#include <vector>", "#<b>include</b> &lt;<a href=\"/source/s?path=vector\">vector</a>&gt;"},
        };

        for (String[] s : testData) {
            StringReader in = new StringReader(s[0]);
            StringWriter out = new StringWriter();
            JFlexXref xref = new JFlexXref(klass.getConstructor(Reader.class).
                newInstance(in));
            xref.write(out);
            assertEquals(FIRST_LINE_PREAMBLE + s[1], out.toString());
        }
    }

    /**
     * Verify that template parameters are treated as class names rather than
     * filenames.
     */
    @Test
    void testCxxXrefTemplateParameters() throws Exception {
        StringReader in = new StringReader("#include <vector>\nclass MyClass;\nstd::vector<MyClass> *v;");
        StringWriter out = new StringWriter();
        JFlexXref xref = new JFlexXref(new CxxXref(in));
        xref.write(out);
        assertTrue(out.toString().contains("&lt;<a href=\"/source/s?defs=MyClass\""),
                "Link to search for definition of class not found");
    }

    /**
     * Verify that ShXref handles here-documents. Bug #18198.
     */
    @Test
    void testShXrefHeredoc() throws IOException {
        final String SH_HERE_DOC = "cat<<EOF\n" +
            "This shouldn't cause any problem.\n" +
            "EOF\n" +
            "var='some string'\n";
        StringReader in = new StringReader(
                SH_HERE_DOC);

        JFlexXref xref = new JFlexXref(new ShXref(in));
        StringWriter out = new StringWriter();
        xref.write(out);

        String xout = out.toString();
        String[] result = xout.split("\n");

        // The single-quote on line 2 shouldn't start a string literal.
        assertTrue(result[1].endsWith("This shouldn&apos;t cause any problem."), "Line 2 of:\n" + xout);

        // The string literal on line 4 should be recognized as one.
        assertTrue(result[3].endsWith("=<span class=\"s\">&apos;some string&apos;</span>"), "Line 4 of:\n" + xout);
    }

    /**
     * Test that JavaXref handles empty Java comments. Bug #17885.
     */
    @Test
    void testEmptyJavaComment() throws IOException {
        StringReader in = new StringReader("/**/\nclass xyz { }\n");
        JFlexXref xref = new JFlexXref(new JavaXref(in));
        StringWriter out = new StringWriter();
        xref.write(out);
        // Verify that the comment's <span> block is terminated.
        assertTrue(out.toString().contains("<span class=\"c\">/**/</span>"));
    }

    @Test
    void bug18586() throws IOException, InterruptedException {
        String filename = repository.getSourceRoot() + "/sql/bug18586.sql";
        try (Reader in = new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8)) {
            JFlexXref xref = new JFlexXref(new SQLXref(in));
            xref.setDefs(ctags.doCtags(filename));
            // The next call used to fail with an ArrayIndexOutOfBoundsException.
            xref.write(new StringWriter());
        }
    }

    /**
     * Test that unterminated heredocs don't cause infinite loop in ShXref.
     * This originally became a problem after upgrade to JFlex 1.5.0.
     */
    @Test
    void unterminatedHeredoc() throws IOException {
        JFlexXref xref = new JFlexXref(new ShXref(new StringReader(
                "cat << EOF\nunterminated heredoc")));

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
    void truncatedUuencodedFile() throws IOException {
        JFlexXref xref = new JFlexXref(new UuencodeXref(
                new StringReader("begin 644 test.txt\n")));

        // Generating the xref used to loop forever.
        StringWriter out = new StringWriter();
        xref.write(out);

        assertLinesEqual("UuencodeXref truncated",
                "<a class=\"l\" name=\"1\" href=\"#1\">1</a>"
                + "<strong>begin</strong> <em>644</em> "
                + "<a href=\"/source/s?full=test.txt\">test.txt</a>"
                + "<span class=\"c\">\n"
                + "<a class=\"l\" name=\"2\" href=\"#2\">2</a></span>",
                out.toString());
    }

    /**
     * Test that CSharpXref correctly handles verbatim strings that end with backslash.
     */
    @Test
    void testCsharpXrefVerbatimString() throws IOException {
        StringReader in = new StringReader("test(@\"\\some_windows_path_in_a_string\\\");");
        JFlexXref xref = new JFlexXref(new CSharpXref(in));
        StringWriter out = new StringWriter();
        xref.write(out);
        assertTrue(out.toString().contains("<span class=\"s\">@&quot;\\some_windows_path_in_a_string\\&quot;</span>"));
    }

    /**
     * Test that special characters in URLs are escaped in the xref.
     */
    @Test
    void testEscapeLink() throws IOException {
        StringReader in = new StringReader("http://www.example.com/?a=b&c=d");
        JFlexXref xref = new JFlexXref(new PlainXref(in));
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
    void testJFlexRule() throws Exception {
        StringReader in = new StringReader("\\\" { yybegin(STRING); }");
        // JFlex files are usually analyzed with CAnalyzer.
        JFlexXref xref = new JFlexXref(new CXref(in));
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
    void testUnterminatedElements() throws Exception {
        for (String str : Arrays.asList("#define STR \"abc\n",
                                        "void f(); /* unterminated comment\n",
                                        "const char c = 'x\n")) {
            StringReader in = new StringReader(str);
            JFlexXref xref = new JFlexXref(new CXref(in));
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
    void testJavaClassAnalyzer() throws Exception {
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
        JavaClassAnalyzerFactory.DEFAULT_INSTANCE.getAnalyzer().analyze(
            doc, src, out);
        // Used to throw SAXParseException.
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new InputSource(new StringReader("<doc>" + out + "</doc>")));
    }

    /**
     * Test that special characters in Fortran files are escaped.
     */
    @Test
    void testFortranSpecialCharacters() throws Exception {
        JFlexXref xref = new JFlexXref(new FortranXref(
            new StringReader("<?php?>")));
        StringWriter out = new StringWriter();
        xref.write(out);
        // Used to throw SAXParseException.
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            new InputSource(new StringReader("<doc>" + out + "</doc>")));
    }
}
