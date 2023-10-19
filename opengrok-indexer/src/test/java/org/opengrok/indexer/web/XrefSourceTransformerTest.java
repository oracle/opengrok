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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Represents a container for tests of {@link XrefSourceTransformer}.
 */
class XrefSourceTransformerTest {

    private static final String XREF_FRAG_DFLT = "<a class=\"hl\" name=\"1\" " +
            "href=\"#1\">1</a><span class=\"c\"># See " +
            "<a href=\"/source/s?path=LICENSE.txt\">LICENSE.txt</a> included " +
            "in this distribution for the specific</span>\n";

    private static final String XREF_FRAG_SVC = "<a class=\"hl\" name=\"1\" " +
            "href=\"#1\">1</a><span class=\"c\"># See " +
            "<a href=\"/svc/s?path=LICENSE.txt\">LICENSE.txt</a> included " +
            "in this distribution for the specific</span>\n";

    private static final String XREF_FRAG_ROOT = "<a class=\"hl\" name=\"1\" " +
            "href=\"#1\">1</a><span class=\"c\"># See " +
            "<a href=\"/s?path=LICENSE.txt\">LICENSE.txt</a> included " +
            "in this distribution for the specific</span>\n";

    XrefSourceTransformer xform;
    StringWriter out;

    @BeforeEach
    void setUp() {
        StringReader rdr = new StringReader(XREF_FRAG_DFLT);
        // Test the normal path of dummy-first then actual data.
        xform = new XrefSourceTransformer(AbstractAnalyzer.DUMMY_READER);
        xform.yyreset(rdr);

        out = new StringWriter();
        xform.setWriter(out);
    }

    @Test
    void testDefaultContext1() throws IOException {
        xform.setContextPath(null);
        while (xform.yylex()) {
        }
        String res = out.toString();
        assertEquals(XREF_FRAG_DFLT, res, "context=null");
    }

    @Test
    void testDefaultContext2() throws IOException {
        xform.setContextPath("source");
        while (xform.yylex()) {
        }
        String res = out.toString();
        assertEquals(XREF_FRAG_DFLT, res, "context=source");
    }

    @Test
    void testDefaultContext3() throws IOException {
        xform.setContextPath("/source");
        while (xform.yylex()) {
        }
        String res = out.toString();
        assertEquals(XREF_FRAG_DFLT, res, "context=/source");
    }

    @Test
    void testDefaultContext4() throws IOException {
        xform.setContextPath("/source/");
        while (xform.yylex()) {
        }
        String res = out.toString();
        assertEquals(XREF_FRAG_DFLT, res, "context=/source/");
    }

    @Test
    void testSvcContext() throws IOException {
        xform.setContextPath("svc");
        while (xform.yylex()) {
        }
        String res = out.toString();
        assertEquals(XREF_FRAG_SVC, res, "context=svc");
    }

    @Test
    void testRootContext() throws IOException {
        xform.setContextPath("/");
        while (xform.yylex()) {
        }
        String res = out.toString();
        assertEquals(XREF_FRAG_ROOT, res, "context=/");
    }
}
