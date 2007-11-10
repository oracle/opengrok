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
    public void Htmlize() {
        String[][] input_output = {
            { "This is a test", "This is a test" },
            { "Newline\nshould become <br/>",
                      "Newline<br/>should become &lt;br/&gt;" },
            { "Open & Grok", "Open &amp; Grok" },
            { "&amp;&lt;&gt;", "&amp;amp;&amp;lt;&amp;gt;" },
        };
        for (String[] in_out : input_output) {
            // 1 arg
            assertEquals(in_out[1], Util.Htmlize(in_out[0]));
            // 2 args
            StringBuilder sb = new StringBuilder();
            Util.Htmlize(in_out[0], sb);
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

        assertEquals("<a href=\"/r/a/\">a</a>/<a href=\"/r/a/b\">b</a>",
                Util.breadcrumbPath("/r/", "a/b"));
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
        assertEquals("\n<a class=\"l\" name=\"42\">     42 </a>",
                     out.toString());

        out.getBuffer().setLength(0); // clear buffer
        Util.readableLine(110, out, null);
        assertEquals("\n<a class=\"hl\" name=\"110\">    110 </a>",
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
        assertEquals("//x/yz/%23%23/+/+%3F",
                     Util.URIEncodePath("//x/yz/##/ / ?"));
    }

    @Test
    public void formQuoteEscape() {
        assertEquals("", Util.formQuoteEscape(null));
        assertEquals("abc", Util.formQuoteEscape("abc"));
        assertEquals("&quot;abc&quot;", Util.formQuoteEscape("\"abc\""));
    }

}