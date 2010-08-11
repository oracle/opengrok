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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.search.context;

import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.lucene.queryParser.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.w3c.dom.Document;
import static org.junit.Assert.*;

public class ContextTest {
    /**
     * The value returned by {@link RuntimeEnvironment#isQuickContextScan()}
     * before the test is run. Will be used to restore the flag after each
     * test case.
     */
    private boolean savedQuickContextScanFlag;

    @Before
    public void setUp() {
        // Save initial value of the quick context scan flag.
        savedQuickContextScanFlag =
                RuntimeEnvironment.getInstance().isQuickContextScan();
    }

    @After
    public void tearDown() {
        // Restore the initial value of the quick context scan flag.
        RuntimeEnvironment.getInstance().
                setQuickContextScan(savedQuickContextScanFlag);
    }

    /**
     * Tests for the isEmpty() method.
     */
    @Test
    public void testIsEmpty() throws ParseException {
        String term = "qwerty";

        // Definition search should be used
        QueryBuilder qb = new QueryBuilder().setDefs(term);
        Context c = new Context(qb.build(), qb.getQueries());
        assertFalse(c.isEmpty());

        // Symbol search should be used
        qb = new QueryBuilder().setRefs(term);
        c = new Context(qb.build(), qb.getQueries());
        assertFalse(c.isEmpty());

        // Full search should be used
        qb = new QueryBuilder().setFreetext(term);
        c = new Context(qb.build(), qb.getQueries());
        assertFalse(c.isEmpty());

        // History search should not be used
        qb = new QueryBuilder().setHist(term);
        c = new Context(qb.build(), qb.getQueries());
        assertTrue(c.isEmpty());

        // Path search should not be used
        qb = new QueryBuilder().setPath(term);
        c = new Context(qb.build(), qb.getQueries());
        assertTrue(c.isEmpty());

        // Combined search should be fine
        qb = new QueryBuilder().setHist(term).setFreetext(term);
        c = new Context(qb.build(), qb.getQueries());
        assertFalse(c.isEmpty());
    }

    /**
     * Tests for the getContext() method.
     */
    @Test
    public void testGetContext() throws ParseException {
        testGetContext(true, true);   // limited scan, output to list
        testGetContext(false, true);  // unlimited scan, output to list
        testGetContext(true, false);  // limited scan, output to writer
        testGetContext(false, false); // unlimited scan, output to writer
    }

    /**
     * Helper method for testing various paths through the getContext() method.
     *
     * @param limit true if limited, quick context scan should be used
     * @param hitList true if output should be written to a list instead of
     * a writer
     */
    private void testGetContext(boolean limit, boolean hitList)
            throws ParseException {
        StringReader in = new StringReader("abc def ghi\n");
        StringWriter out = hitList ? null : new StringWriter();
        List<Hit> hits = hitList ? new ArrayList<Hit>() : null;

        RuntimeEnvironment.getInstance().setQuickContextScan(limit);

        // Search freetext for the term "def"
        QueryBuilder qb = new QueryBuilder().setFreetext("def");
        Context c = new Context(qb.build(), qb.getQueries());
        assertTrue(c.getContext(in, out, "", "", "", null, limit, hits));

        if (hitList) {
            assertEquals(1, hits.size());
            assertEquals("1", hits.get(0).getLineno());
        }

        String expectedOutput = hitList ?
            "abc <b>def</b> ghi" :
            "<a class=\"s\" href=\"#1\"><span class=\"l\">1</span> " +
                     "abc <b>def</b> ghi</a><br/>";

        String actualOutput = hitList ? hits.get(0).getLine() : out.toString();

        assertEquals(expectedOutput, actualOutput);

        // Search with definitions
        Definitions defs = new Definitions();
        defs.addTag(1, "def", "type", "text");
        in = new StringReader("abc def ghi\n");
        out = hitList ? null : new StringWriter();
        hits = hitList ? new ArrayList<Hit>() : null;
        qb = new QueryBuilder().setDefs("def");
        c = new Context(qb.build(), qb.getQueries());
        assertTrue(c.getContext(in, out, "", "", "", defs, limit, hits));

        if (hitList) {
            assertEquals(1, hits.size());
            assertEquals("1", hits.get(0).getLineno());
        }

        expectedOutput = hitList ?
            "abc <b>def</b> ghi" :
            "<a class=\"s\" href=\"#1\"><span class=\"l\">1</span> " +
                     "abc <b>def</b> ghi</a> <i> type</i> <br/>";
        actualOutput = hitList ? hits.get(0).getLine() : out.toString();
        assertEquals(expectedOutput, actualOutput);

        // Search with no input (will search definitions)
        in = null;
        out = hitList ? null : new StringWriter();
        hits = hitList ? new ArrayList<Hit>() : null;
        qb = new QueryBuilder().setDefs("def");
        c = new Context(qb.build(), qb.getQueries());
        assertTrue(c.getContext(in, out, "", "", "", defs, limit, hits));

        if (hitList) {
            assertEquals(1, hits.size());
            assertEquals("1", hits.get(0).getLineno());
        }

        expectedOutput = hitList ?
            "text" :
            "<a class=\"s\" href=\"#1\"><span class=\"l\">1</span> " +
                     "text</a> <i> type </i><br/>";
        actualOutput = hitList ? hits.get(0).getLine() : out.toString();
        assertEquals(expectedOutput, actualOutput);

        // Search with no results
        in = new StringReader("abc def ghi\n");
        out = hitList ? null : new StringWriter();
        hits = hitList ? new ArrayList<Hit>() : null;
        qb = new QueryBuilder().setFreetext("no_match");
        c = new Context(qb.build(), qb.getQueries());
        assertFalse(c.getContext(in, out, "", "", "", null, limit, hits));
        if (hitList) {
            assertEquals(0, hits.size());
        } else {
            assertEquals("", out.toString());
        }

        // History search (should not show source context)
        in = new StringReader("abc def ghi\n");
        out = hitList ? null : new StringWriter();
        hits = hitList ? new ArrayList<Hit>() : null;
        qb = new QueryBuilder().setHist("abc");
        c = new Context(qb.build(), qb.getQueries());
        assertFalse(c.getContext(in, out, "", "", "", null, limit, hits));
        if (hitList) {
            assertEquals(0, hits.size());
        } else {
            assertEquals("", out.toString());
        }
    }

    /**
     * Test that we don't get an {@code ArrayIndexOutOfBoundsException} when
     * a long (&gt;100 characters) line which contains a match is not
     * terminated with a newline character before the buffer boundary.
     * Bug #383.
     */
    @Test
    public void testLongLineNearBufferBoundary() throws ParseException {
        char[] chars = new char[Context.MAXFILEREAD];
        Arrays.fill(chars, 'a');
        char[] substring = " this is a test ".toCharArray();
        System.arraycopy(substring, 0,
                         chars, Context.MAXFILEREAD - substring.length,
                         substring.length);
        Reader in = new CharArrayReader(chars);
        QueryBuilder qb = new QueryBuilder().setFreetext("test");
        Context c = new Context(qb.build(), qb.getQueries());
        StringWriter out = new StringWriter();
        boolean match =
                c.getContext(in, out, "", "", "", null, true, null);
        assertTrue("No match found", match);
        String s = out.toString();
        assertTrue("Match not written to Writer",
                   s.contains(" this is a <b>test</b>"));
        assertTrue("No match on line #1", s.contains("href=\"#1\""));
    }

    /**
     * Test that we get the [all...] link if a very long line crosses the
     * buffer boundary. Bug 383.
     */
    @Test
    public void testAllLinkWithLongLines() throws ParseException {
        // Create input which consists of one single line longer than
        // Context.MAXFILEREAD.
        StringBuilder sb = new StringBuilder();
        sb.append("search_for_me");
        while (sb.length() <= Context.MAXFILEREAD) {
            sb.append(" more words");
        }
        Reader in = new StringReader(sb.toString());
        StringWriter out = new StringWriter();

        QueryBuilder qb = new QueryBuilder().setFreetext("search_for_me");
        Context c = new Context(qb.build(), qb.getQueries());

        boolean match =
                c.getContext(in, out, "", "", "", null, true, null);
        assertTrue("No match found", match);
        String s = out.toString();
        assertTrue("No [all...] link", s.contains(">all</a>...]"));
    }

    /**
     * Test that a line with more than 100 characters after the first match
     * is truncated, and that &hellip; is appended to show that the line is
     * truncated. Bug 383.
     */
    @Test
    public void testLongTruncatedLine() throws ParseException {
        StringBuilder sb = new StringBuilder();
        sb.append("search_for_me");
        while (sb.length() <= 100) {
            sb.append(" more words");
        }
        sb.append("should not be found");

        Reader in = new StringReader(sb.toString());
        StringWriter out = new StringWriter();

        QueryBuilder qb = new QueryBuilder().setFreetext("search_for_me");
        Context c = new Context(qb.build(), qb.getQueries());

        boolean match =
                c.getContext(in, out, "", "", "", null, true, null);
        assertTrue("No match found", match);
        String s = out.toString();
        assertTrue("Match not listed", s.contains("<b>search_for_me</b>"));
        assertFalse("Line not truncated", s.contains("should not be found"));
        assertTrue("Ellipsis not found", s.contains("&hellip;"));
    }

    /**
     * Test that valid HTML is generated for a match that spans multiple
     * lines. It used to nest the tags incorrectly. Bug #15632.
     */
    @Test
    public void testMultiLineMatch() throws Exception {
        StringReader in = new StringReader("a\nb\nc\n");
        StringWriter out = new StringWriter();

        // XML boilerplate
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<document>\n");

        // Search for a multi-token phrase that spans multiple lines in the
        // input file. The generated HTML fragment is inserted inside a root
        // element so that the StringWriter contains a valid XML document.
        QueryBuilder qb = new QueryBuilder().setFreetext("\"a b c\"");
        Context c = new Context(qb.build(), qb.getQueries());
        assertTrue(
                "No match found",
                c.getContext(in, out, "", "", "", null, true, null));

        // Close the XML document body
        out.append("\n</document>");

        // Check that valid XML was generated. This call used to fail with
        // SAXParseException: [Fatal Error] :3:55: The element type "b" must
        // be terminated by the matching end-tag "</b>".
        assertNotNull(parseXML(out.toString()));
    }

    /**
     * Parse the XML document contained in a string.
     *
     * @param document string with the contents of an XML document
     * @return a DOM representation of the document
     * @throws Exception if the document cannot be parsed
     */
    private Document parseXML(String document) throws Exception {
        ByteArrayInputStream in =
                new ByteArrayInputStream(document.getBytes("UTF-8"));
        return DocumentBuilderFactory.newInstance().
                newDocumentBuilder().parse(in);
    }
}
