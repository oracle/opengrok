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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.search.context;

import java.io.CharArrayReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import org.apache.lucene.queryParser.ParseException;
import org.junit.Test;
import org.opensolaris.opengrok.search.QueryBuilder;
import static org.junit.Assert.*;

public class ContextTest {

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
}
