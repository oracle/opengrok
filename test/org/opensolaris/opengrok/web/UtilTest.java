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
package org.opensolaris.opengrok.web;

import java.io.StringWriter;
import java.util.Locale;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test of the methods in <code>org.opensolaris.opengrok.web.Util</code>.
 */
public class UtilTest {
    private static Locale savedLocale;

    @BeforeClass
    public static void setUpClass() {
        // Some of the methods have different results in different locales.
        // Set locale to en_US for these tests.
        savedLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterClass
    public static void tearDownClass() {
        Locale.setDefault(savedLocale);
        savedLocale = null;
    }

    @Test
    public void htmlize() {
        String[][] input_output = {
            {"This is a test", "This is a test" },
            {"Newline\nshould become <br/>",
                      "Newline<br/>should become &lt;br/&gt;" },
            {"Open & Grok", "Open &amp; Grok" },
            {"&amp;&lt;&gt;", "&amp;amp;&amp;lt;&amp;gt;" },
        };
        for (String[] in_out : input_output) {
            // 1 arg
            assertEquals(in_out[1], Util.htmlize(in_out[0]));
            // 2 args
            StringBuilder sb = new StringBuilder();
            Util.htmlize(in_out[0], sb);
            assertEquals(in_out[1], sb.toString());
        }
    }

    @Test
    public void breadcrumbPath() {
        assertEquals(null, Util.breadcrumbPath("/root/", null));

        // Are these two correct? Why don't we create links?
        assertEquals("", Util.breadcrumbPath("/root/", ""));
        assertEquals("x", Util.breadcrumbPath("/root/", "x"));

        assertEquals("<a href=\"/root/xx\">xx</a>",
                Util.breadcrumbPath("/root/", "xx"));

        assertEquals("<a href=\"/r/a\">a</a>/<a href=\"/r/a/b\">b</a>",
                Util.breadcrumbPath("/r/", "a/b"));

        assertEquals("<a href=\"/r/a\">a</a>/<a href=\"/r/a/b\">b</a>/",
                Util.breadcrumbPath("/r/", "a/b/"));

        assertEquals("<a href=\"/r/java\">java</a>." +
                "<a href=\"/r/java/lang\">lang</a>." +
                "<a href=\"/r/java/lang/String\">String</a>",
                Util.breadcrumbPath("/r/", "java.lang.String", '.'));

        assertEquals("<a href=\"/root/xx&project=y\">xx</a>",
                Util.breadcrumbPath("/root/", "xx", '/', "&project=y", false));

        assertEquals("<a href=\"/root/xx&project=y\">xx</a>",
                Util.breadcrumbPath("/root/", "xx", '/', "&project=y", true));

        assertEquals("<a href=\"/r/\">..</a>/" +
                "<a href=\"/r/a\">a</a>/" +
                "<a href=\"/r/a/b\">b</a>/" +
                "<a href=\"/r/a\">..</a>/" +
                "<a href=\"/r/a/c\">c</a>/" +
                "/" +
                "<a href=\"/r/a/c/d\">d</a>",
                Util.breadcrumbPath("/r/", "../a/b/../c//d", '/', "", true));
    }

    @Test
    public void redableSize() {
        assertEquals("0", Util.redableSize(0));
        assertEquals("1", Util.redableSize(1));
        assertEquals("-1", Util.redableSize(-1));
        assertEquals("1,000", Util.redableSize(1000));
        assertEquals("1K", Util.redableSize(1024));
        assertEquals("2.4K", Util.redableSize(2500));
        assertEquals("<b>1.4M</b>", Util.redableSize(1474560));
        assertEquals("<b>3,584.4M</b>", Util.redableSize(3758489600L));
        assertEquals("<b>8,796,093,022,208M</b>",
                     Util.redableSize(Long.MAX_VALUE));
    }

    @Test
    public void readableLine() throws Exception {
        StringWriter out = new StringWriter();
        Util.readableLine(42, out, null);
        assertEquals("\n<a class=\"l\" name=\"42\" href=\"#42\">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;42&nbsp;</a>",
                     out.toString());

        out.getBuffer().setLength(0); // clear buffer
        Util.readableLine(110, out, null);
        assertEquals("\n<a class=\"hl\" name=\"110\" href=\"#110\">&nbsp;&nbsp;&nbsp;&nbsp;110&nbsp;</a>",
                     out.toString());
    }

    @Test
    public void uid() {
        assertEquals("\u0000etc\u0000passwd\u0000date",
                     Util.uid("/etc/passwd", "date"));
    }

    @Test
    public void uid2url() {
        assertEquals("/etc/passwd", Util.uid2url(
                Util.uid("/etc/passwd", "date")));
    }

    @Test
    public void URIEncode() {
        assertEquals("", Util.URIEncode(""));
        assertEquals("a+b", Util.URIEncode("a b"));
        assertEquals("a%23b", Util.URIEncode("a#b"));
        assertEquals("a%2Fb", Util.URIEncode("a/b"));
        assertEquals("README.txt", Util.URIEncode("README.txt"));
    }

    @Test
    public void URIEncodePath() {
        assertEquals("", Util.URIEncodePath(""));
        assertEquals("/", Util.URIEncodePath("/"));
        assertEquals("a", Util.URIEncodePath("a"));
        assertEquals("a+b", Util.URIEncodePath("a+b"));
        assertEquals("a%20b", Util.URIEncodePath("a b"));
        assertEquals("/a//x/yz/%23%23/%20/%20%3F",
                     Util.URIEncodePath("/a//x/yz/##/ / ?"));
    }

    @Test
    public void formQuoteEscape() {
        assertEquals("", Util.formQuoteEscape(null));
        assertEquals("abc", Util.formQuoteEscape("abc"));
        assertEquals("&quot;abc&quot;", Util.formQuoteEscape("\"abc\""));
    }

    @Test
    public void diffline() {
        String[] strings=Util.diffline("\"(ses_id, mer_id, pass_id, \" + refCol +\" , mer_ref, amnt, cur, ps_id, ret_url, d_req_time, d_req_mil, h_resp_time, h_resp_mil) \"","\"(ses_id, mer_id, pass_id, \" + refCol +\" , mer_ref, amnt, cur, ps_id, ret_url, exp_url, d_req_time, d_req_mil, h_resp_time, h_resp_mil) \"");        
        assertEquals(strings[0],"\"(ses_id, mer_id, pass_id, \" + refCol +\" , mer_ref, amnt, cur, ps_id, ret_url, d_req_time, d_req_mil, h_resp_time, h_resp_mil) \"");
        assertEquals(strings[1],"\"(ses_id, mer_id, pass_id, \" + refCol +\" , mer_ref, amnt, cur, ps_id, ret_url, <span class=\"a\">exp_url, </span>d_req_time, d_req_mil, h_resp_time, h_resp_mil) \"");
        strings=Util.diffline("\"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\", values);","\"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\", values);");
        assertEquals(strings[0],"\"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\", values);");
        assertEquals(strings[1],"\"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?<span class=\"a\">, ?</span>)\", values);");
        strings=Util.diffline("char    *config_list = NULL;","char    **config_list = NULL;");
        assertEquals(strings[0],"char    *config_list = NULL;");
        assertEquals(strings[1],"char    *<span class=\"a\">*</span>config_list = NULL;");
        strings=Util.diffline("* An error occured or there is non-numeric stuff at the end","* An error occurred or there is non-numeric stuff at the end");
        assertEquals(strings[0],"* An error occured or there is non-numeric stuff at the end");
        assertEquals(strings[1],"* An error occur<span class=\"a\">r</span>ed or there is non-numeric stuff at the end");
    }
}

