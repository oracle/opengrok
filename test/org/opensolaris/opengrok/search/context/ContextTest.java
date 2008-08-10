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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.search.context;

import java.io.CharArrayReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Arrays;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContextTest {

    /**
     * Test that we don't get an {@code ArrayIndexOutOfBoundsException} when
     * a long (&gt;100 characters) line which contains a match is not
     * terminated with a newline character before the buffer boundary.
     * Bug #383.
     */
    @Test
    public void testLongLineNearBufferBoundary() {
        char[] chars = new char[Context.MAXFILEREAD];
        Arrays.fill(chars, 'a');
        char[] substring = " this is a test ".toCharArray();
        System.arraycopy(substring, 0,
                         chars, Context.MAXFILEREAD - substring.length,
                         substring.length);
        Reader in = new CharArrayReader(chars);
        Term t = new Term("full", "test");
        TermQuery tq = new TermQuery(t);
        Context c = new Context(tq);
        StringWriter out = new StringWriter();
        boolean match =
                c.getContext(in, out, "", "", "", null, true, null);
        assertTrue("No match found", match);
        String s = out.toString();
        assertTrue("Match not written to Writer",
                   s.contains(" this is a <b>test</b>"));
        assertTrue("No match on line #1", s.contains("href=\"#1\""));
    }

}
