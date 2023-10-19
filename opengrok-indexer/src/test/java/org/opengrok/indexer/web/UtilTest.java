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
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.MERCURIAL;

/**
 * Test of the methods in <code>org.opengrok.indexer.web.Util</code>.
 */
class UtilTest {

    private static Locale savedLocale;

    @BeforeAll
    static void setUpClass() {
        // Some methods have different results in different locales.
        // Set locale to en_US for these tests.
        savedLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterAll
    static void tearDownClass() {
        Locale.setDefault(savedLocale);
        savedLocale = null;
    }

    @Test
    void htmlize() throws IOException {
        String[][] input_output = {
                {"This is a test", "This is a test"},
                {"Newline\nshould become <br/>", "Newline<br/>should become &lt;br/&gt;"},
                {"Open & Grok", "Open &amp; Grok"},
                {"&amp;&lt;&gt;", "&amp;amp;&amp;lt;&amp;gt;"}};
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
    void breadcrumbPath() {
        assertNull(Util.breadcrumbPath("/root/", null));

        assertEquals("", Util.breadcrumbPath("/root/", ""));

        assertEquals("<a href=\"/root/x\">x</a>",
                Util.breadcrumbPath("/root/", "x"));
        assertEquals("<a href=\"/root/xx\">xx</a>",
                Util.breadcrumbPath("/root/", "xx"));

        // parent directories have a trailing slash in href
        assertEquals("<a href=\"/r/a/\">a</a>/<a href=\"/r/a/b\">b</a>",
                Util.breadcrumbPath("/r/", "a/b"));
        // if basename is a dir (ends with file seperator), href link also
        // ends with a '/'
        assertEquals("<a href=\"/r/a/\">a</a>/<a href=\"/r/a/b/\">b</a>/",
                Util.breadcrumbPath("/r/", "a/b/"));
        // should work the same way with a '.' as file separator
        assertEquals("<a href=\"/r/java/\">java</a>."
                        + "<a href=\"/r/java/lang/\">lang</a>."
                        + "<a href=\"/r/java/lang/String\">String</a>",
                Util.breadcrumbPath("/r/", "java.lang.String", '.'));
        // suffix added to the link?
        assertEquals("<a href=\"/root/xx&project=y\">xx</a>",
                Util.breadcrumbPath("/root/", "xx", '/', "&project=y", false));
        // compact: path needs to be resolved to /xx and no link is added
        // for the virtual root directory (parent) but emitted as plain text.
        // Prefix gets just prefixed as is and not mangled wrt. path -> "//"
        assertEquals("/<a href=\"/root//xx&project=y\">xx</a>",
                Util.breadcrumbPath("/root/", "../xx", '/', "&project=y", true));
        // relative pathes are resolved wrt. / , so path resolves to /a/c/d
        assertEquals("/<a href=\"/r//a/\">a</a>/"
                        + "<a href=\"/r//a/c/\">c</a>/"
                        + "<a href=\"/r//a/c/d\">d</a>",
                Util.breadcrumbPath("/r/", "../a/b/../c//d", '/', "", true));
    }

    @Test
    void readableSize() {
        assertEquals("0 ", Util.readableSize(0));
        assertEquals("1 ", Util.readableSize(1));
        assertEquals("-1 ", Util.readableSize(-1));
        assertEquals("1,000 ", Util.readableSize(1000));
        assertEquals("1 KiB", Util.readableSize(1024));
        assertEquals("2.4 KiB", Util.readableSize(2500));
        assertEquals("<b>1.4 MiB</b>", Util.readableSize(1474560));
        assertEquals("<b>3.5 GiB</b>", Util.readableSize(3758489600L));
        assertEquals("<b>8,589,934,592 GiB</b>",
                Util.readableSize(Long.MAX_VALUE));
    }

    @Test
    void readableLine() throws Exception {
        StringWriter out = new StringWriter();
        // hmmm - where do meaningful tests start?
        Util.readableLine(42, out, null, null, null, null);
        assertEquals("\n<a class=\"l\" name=\"42\" href=\"#42\">42</a>",
                out.toString());

        out.getBuffer().setLength(0); // clear buffer
        Util.readableLine(110, out, null, null, null, null);
        assertEquals("\n<a class=\"hl\" name=\"110\" href=\"#110\">110</a>",
                out.toString());
    }

    @Test
    void path2uid() {
        assertEquals("\u0000etc\u0000passwd\u0000date",
                Util.path2uid("/etc/passwd", "date"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void fixPathIfWindows() {
        assertEquals("/var/opengrok", Util.fixPathIfWindows("\\var\\opengrok"));
    }

    @Test
    void uid2url() {
        assertEquals("/etc/passwd", Util.uid2url(
                Util.path2uid("/etc/passwd", "date")));
    }

    @Test
    void testUriEncode() {
        assertEquals("", Util.uriEncode(""));
        assertEquals("a+b", Util.uriEncode("a b"));
        assertEquals("a%23b", Util.uriEncode("a#b"));
        assertEquals("a%2Fb", Util.uriEncode("a/b"));
        assertEquals("README.txt", Util.uriEncode("README.txt"));
    }

    @Test
    void testUriEncodePath() {
        assertEquals("", Util.uriEncodePath(""));
        assertEquals("/", Util.uriEncodePath("/"));
        assertEquals("a", Util.uriEncodePath("a"));
        assertEquals("%09", Util.uriEncodePath("\t"));
        assertEquals("a%2Bb", Util.uriEncodePath("a+b"));
        assertEquals("a%20b", Util.uriEncodePath("a b"));
        assertEquals("/a//x/yz/%23%23/%20/%20%3F",
                Util.uriEncodePath("/a//x/yz/##/ / ?"));
        assertEquals("foo%3A%3Abar%3A%3Atest.js",
                Util.uriEncodePath("foo::bar::test.js"));
        assertEquals("bl%C3%A5b%C3%A6rsyltet%C3%B8y",
                Util.uriEncodePath("bl\u00E5b\u00E6rsyltet\u00F8y"));
    }

    @Test
    void formQuoteEscape() {
        assertEquals("", Util.formQuoteEscape(null));
        assertEquals("abc", Util.formQuoteEscape("abc"));
        assertEquals("&quot;abc&quot;", Util.formQuoteEscape("\"abc\""));
        assertEquals("&amp;aring;", Util.formQuoteEscape("&aring;"));
    }

    @Test
    void diffline() {
        String[][] tests = {
                {
                        "if (a < b && foo < bar && c > d)",
                        "if (a < b && foo > bar && c > d)",
                        "if (a &lt; b &amp;&amp; foo <span class=\"d\">&lt;</span> bar &amp;&amp; c &gt; d)",
                        "if (a &lt; b &amp;&amp; foo <span class=\"a\">&gt;</span> bar &amp;&amp; c &gt; d)"
                },
                {
                        "foo << 1",
                        "foo >> 1",
                        "foo <span class=\"d\">&lt;&lt;</span> 1",
                        "foo <span class=\"a\">&gt;&gt;</span> 1"
                },
                {
                        "\"(ses_id, mer_id, pass_id, \" + refCol +\" , mer_ref, amnt, "
                                + "cur, ps_id, ret_url, d_req_time, d_req_mil, h_resp_time, "
                                + "h_resp_mil) \"",
                        "\"(ses_id, mer_id, pass_id, \" + refCol +\" , mer_ref, amnt, "
                                + "cur, ps_id, ret_url, exp_url, d_req_time, d_req_mil, "
                                + "h_resp_time, h_resp_mil) \"",
                        "&quot;(ses_id, mer_id, pass_id, &quot; + refCol +&quot; , mer_ref, amnt, "
                                + "cur, ps_id, ret_url, d_req_time, d_req_mil, h_resp_time, "
                                + "h_resp_mil) &quot;",
                        "&quot;(ses_id, mer_id, pass_id, &quot; + refCol +&quot; , mer_ref, amnt, "
                                + "cur, ps_id, ret_url, <span class=\"a\">exp_url, "
                                + "</span>d_req_time, d_req_mil, h_resp_time, h_resp_mil) &quot;"
                },
                {
                        "\"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\", values);",
                        "\"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\", values);",
                        "&quot;VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)&quot;, values);",
                        "&quot;VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?<span "
                                + "class=\"a\">, ?</span>)&quot;, values);"
                },
                {
                        "char    *config_list = NULL;",
                        "char    **config_list = NULL;",
                        "char    *config_list = NULL;",
                        "char    *<span class=\"a\">*</span>config_list = NULL;"
                },
                {
                        "char    **config_list = NULL;",
                        "char    *config_list = NULL;",
                        "char    *<span class=\"d\">*</span>config_list = NULL;",
                        "char    *config_list = NULL;"
                },
                {
                        "* An error occured or there is non-numeric stuff at the end",
                        "* An error occurred or there is non-numeric stuff at the end",
                        "* An error occured or there is non-numeric stuff at the end",
                        "* An error occur<span class=\"a\">r</span>ed or there is "
                                + "non-numeric stuff at the end"
                }
        };
        for (int i = 0; i < tests.length; i++) {
            String[] strings = Util.diffline(
                    new StringBuilder(tests[i][0]),
                    new StringBuilder(tests[i][1]));
            assertEquals(tests[i][2], strings[0], "" + i + "," + 0);
            assertEquals(tests[i][3], strings[1], "" + i + "," + 1);
        }
    }

    @Test
    void testEncode() {
        String[][] tests = new String[][] {
                {"Test <code>title</code>", "Test&nbsp;&#60;code&#62;title&#60;/code&#62;"},
                {"ahoj", "ahoj"},
                {"<>|&\"'", "&#60;&#62;|&#38;&#34;&#39;"},
                {"tab\ttab", "tab&nbsp;&nbsp;&nbsp;&nbsp;tab"},
                {"multi\nline\t\nstring", "multi&lt;br/&gt;line&nbsp;&nbsp;&nbsp;&nbsp;&lt;br/&gt;string"}
        };

        for (String[] test : tests) {
            assertEquals(test[1], Util.encode(test[0]));
        }
    }

    @Test
    void dumpConfiguration() throws Exception {
        StringBuilder out = new StringBuilder();
        Util.dumpConfiguration(out);
        String s = out.toString();

        // Verify that we got a table.
        assertTrue(s.startsWith("<table"));

        // Verify that the output is well-formed.
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + s;
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void jsStringLiteral() {
        assertEquals("\"abc\\n\\r\\\"\\\\\"",
                Util.jsStringLiteral("abc\n\r\"\\"));
    }

    @Test
    void stripPathPrefix() {
        assertEquals("/", Util.stripPathPrefix("/", "/"));
        assertEquals("/abc", Util.stripPathPrefix("/abc", "/abc"));
        assertEquals("/abc/", Util.stripPathPrefix("/abc", "/abc/"));
        assertEquals("/abc", Util.stripPathPrefix("/abc/", "/abc"));
        assertEquals("/abc/", Util.stripPathPrefix("/abc/", "/abc/"));
        assertEquals("abc", Util.stripPathPrefix("/", "/abc"));
        assertEquals("abc/def", Util.stripPathPrefix("/", "/abc/def"));
        assertEquals("def", Util.stripPathPrefix("/abc", "/abc/def"));
        assertEquals("def", Util.stripPathPrefix("/abc/", "/abc/def"));
        assertEquals("/abcdef", Util.stripPathPrefix("/abc", "/abcdef"));
        assertEquals("/abcdef", Util.stripPathPrefix("/abc/", "/abcdef"));
        assertEquals("def/ghi", Util.stripPathPrefix("/abc", "/abc/def/ghi"));
        assertEquals("def/ghi", Util.stripPathPrefix("/abc/", "/abc/def/ghi"));
    }

    @Test
    void testSlider() {
        /*
         * Test if contains all five pages for 55 results paginated by 10
         */
        for (int i = 0; i < 10; i++) {
            for (int j = 1; j <= 5; j++) {
                assertTrue(Util.createSlider(i * 10, 10, 55).contains(">" + j + "<"), "Contains page " + j);
            }
        }

        assertFalse(Util.createSlider(0, 10, 4).contains(">1<"), "Does not contain page 1");
        assertFalse(Util.createSlider(0, 10, 2).contains(">5<"), "Does not contain page 5");
        assertFalse(Util.createSlider(0, 10, 0).contains(">1<"), "Does not contain page 1");
    }

    @Test
    void testIsUrl() {
        assertTrue(Util.isHttpUri("http://www.example.com"));
        assertTrue(Util.isHttpUri("http://example.com"));
        assertTrue(Util.isHttpUri("https://example.com"));
        assertTrue(Util.isHttpUri("https://www.example.com"));
        assertTrue(Util.isHttpUri("http://www.example.com?param=1&param2=2"));
        assertTrue(Util.isHttpUri("http://www.example.com/other/page"));
        assertTrue(Util.isHttpUri("https://www.example.com?param=1&param2=2"));
        assertTrue(Util.isHttpUri("https://www.example.com/other/page"));
        assertTrue(Util.isHttpUri("http://www.example.com:80/other/page"));
        assertTrue(Util.isHttpUri("http://www.example.com:8080/other/page"));
        assertTrue(Util.isHttpUri("https://www.example.com:80/other/page"));
        assertTrue(Util.isHttpUri("https://www.example.com:8080/other/page"));

        assertFalse(Util.isHttpUri("git@github.com:OpenGrok/OpenGrok"));
        assertFalse(Util.isHttpUri("hg@mercurial.com:OpenGrok/OpenGrok"));
        assertFalse(Util.isHttpUri("ssh://git@github.com:OpenGrok/OpenGrok"));
        assertFalse(Util.isHttpUri("ldap://example.com/OpenGrok/OpenGrok"));
        assertFalse(Util.isHttpUri("smtp://example.com/OpenGrok/OpenGrok"));
    }

    @Test
    void testRedactUrl() {
        assertEquals("/foo/bar", Util.redactUrl("/foo/bar"));
        assertEquals("http://foo/bar?r=xxx", Util.redactUrl("http://foo/bar?r=xxx"));
        assertEquals("http://" + Util.REDACTED_USER_INFO + "@foo/bar?r=xxx",
                Util.redactUrl("http://user@foo/bar?r=xxx"));
        assertEquals("http://" + Util.REDACTED_USER_INFO + "@foo/bar?r=xxx",
                Util.redactUrl("http://user:pass@foo/bar?r=xxx"));
    }

    @Test
    void testLinkify() throws URISyntaxException, MalformedURLException {
        assertTrue(Util.linkify("http://www.example.com")
                .matches("<a.*?href=\"http://www\\.example\\.com\".*?>.*?</a>"));
        assertTrue(Util.linkify("https://example.com")
                .matches("<a.*?href=\"https://example\\.com\".*?>.*?</a>"));
        assertTrue(Util.linkify("http://www.example.com?param=1&param2=2")
                .matches("<a.*?href=\"http://www\\.example\\.com\\?param=1&param2=2\".*?>.*?</a>"));
        assertTrue(Util.linkify("https://www.example.com:8080/other/page")
                .matches("<a.*?href=\"https://www\\.example\\.com:8080/other/page\".*?>.*?</a>"));

        assertTrue(Util.linkify("http://www.example.com", true).contains("target=\"_blank\""));
        assertTrue(Util.linkify("https://example.com", true).contains("target=\"_blank\""));
        assertTrue(Util.linkify("http://www.example.com?param=1&param2=2", true).contains("target=\"_blank\""));
        assertTrue(Util.linkify("https://www.example.com:8080/other/page", true).contains("target=\"_blank\""));
        assertTrue(Util.linkify("https://www.example.com:8080/other/page", true).contains("rel=\"noreferrer\""));

        assertFalse(Util.linkify("http://www.example.com", false).contains("target=\"_blank\""));
        assertFalse(Util.linkify("https://example.com", false).contains("target=\"_blank\""));
        assertFalse(Util.linkify("http://www.example.com?param=1&param2=2", false).contains("target=\"_blank\""));
        assertFalse(Util.linkify("https://www.example.com:8080/other/page", false).contains("target=\"_blank\""));

        assertEquals("git@github.com:OpenGrok/OpenGrok", Util.linkify("git@github.com:OpenGrok/OpenGrok"));
        assertEquals("hg@mercurial.com:OpenGrok/OpenGrok", Util.linkify("hg@mercurial.com:OpenGrok/OpenGrok"));
        assertEquals("ssh://git@github.com:OpenGrok/OpenGrok", Util.linkify("ssh://git@github.com:OpenGrok/OpenGrok"));
        assertEquals("ldap://example.com/OpenGrok/OpenGrok", Util.linkify("ldap://example.com/OpenGrok/OpenGrok"));
        assertEquals("smtp://example.com/OpenGrok/OpenGrok", Util.linkify("smtp://example.com/OpenGrok/OpenGrok"));
        assertEquals("just some crazy text", Util.linkify("just some crazy text"));

        // escaping url
        assertTrue(Util.linkify("http://www.example.com/\"quotation\"/else")
                .contains("href=\"" + Util.encodeURL("http://www.example.com/\"quotation\"/else") + "\""));
        assertTrue(Util.linkify("https://example.com/><\"")
                .contains("href=\"" + Util.encodeURL("https://example.com/><\"") + "\""));
        assertTrue(Util.linkify("http://www.example.com?param=1&param2=2&param3=\"quoted>\"")
                .contains("href=\"" + Util.encodeURL("http://www.example.com?param=1&param2=2&param3=\"quoted>\"") + "\""));
        // escaping titles
        assertTrue(Util.linkify("http://www.example.com/\"quotation\"/else")
                .contains("title=\"Link to " + Util.encode("http://www.example.com/\"quotation\"/else") + "\""));
        assertTrue(Util.linkify("https://example.com/><\"")
                .contains("title=\"Link to " + Util.encode("https://example.com/><\"") + "\""));
        assertTrue(Util.linkify("http://www.example.com?param=1&param2=2&param3=\"quoted>\"")
                .contains("title=\"Link to " + Util.encode("http://www.example.com?param=1&param2=2&param3=\"quoted>\"") + "\""));
    }

    @Test
    void testBuildLink() throws URISyntaxException, MalformedURLException {
        assertEquals("<a href=\"http://www.example.com\">link</a>", Util.buildLink("link", "http://www.example.com"));
        assertEquals("<a href=\"http://www.example.com?url=xasw&beta=gama\">link</a>",
                Util.buildLink("link", "http://www.example.com?url=xasw&beta=gama"));

        String link = Util.buildLink("link", "http://www.example.com", true);
        assertTrue(link.contains("href=\"http://www.example.com\""));
        assertTrue(link.contains("target=\"_blank\""));

        link = Util.buildLink("link", "http://www.example.com?url=xasw&beta=gama", true);
        assertTrue(link.contains("href=\"http://www.example.com?url=xasw&beta=gama\""));
        assertTrue(link.contains("target=\"_blank\""));

        Map<String, String> attrs = new TreeMap<>();
        attrs.put("href", "https://www.example.com/abcd/acbd");
        attrs.put("title", "Some important title");
        attrs.put("data-id", "123456");

        link = Util.buildLink("link", attrs);
        assertTrue(link.contains("href=\"https://www.example.com/abcd/acbd\""));
        assertTrue(link.contains("title=\"Some important title\""));
        assertTrue(link.contains("data-id=\"123456\""));
    }

    @Test
    void testBuildLinkInvalidUrl1() {
        assertThrows(MalformedURLException.class, () -> Util.buildLink("link", "www.example.com")); // invalid protocol
    }

    @Test
    void testBuildLinkInvalidUrl2() {
        assertThrows(URISyntaxException.class, () -> Util.buildLink("link", "http://www.exa\"mp\"le.com")); // invalid authority
    }

    @Test
    void testLinkifyPattern() {
        String text
                = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
                + "sed do eiusmod tempor incididunt as per 12345698 ut labore et dolore magna "
                + "aliqua. bug3333fff Ut enim ad minim veniam, quis nostrud exercitation "
                + "ullamco laboris nisi ut aliquip ex ea introduced in 9791216541 commodo consequat. "
                + "Duis aute irure dolor in reprehenderit in voluptate velit "
                + "esse cillum dolore eu fixes 132469187 fugiat nulla pariatur. Excepteur sint "
                + "occaecat bug6478abc cupidatat non proident, sunt in culpa qui officia "
                + "deserunt mollit anim id est laborum.";
        String expected
                = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
                + "sed do eiusmod tempor incididunt as per "
                + "<a href=\"http://www.example.com?bug=12345698\" rel=\"noreferrer\" target=\"_blank\">12345698</a>"
                + " ut labore et dolore magna "
                + "aliqua. bug3333fff Ut enim ad minim veniam, quis nostrud exercitation "
                + "ullamco laboris nisi ut aliquip ex ea introduced in "
                + "<a href=\"http://www.example.com?bug=9791216541\" rel=\"noreferrer\" target=\"_blank\">9791216541</a>"
                + " commodo consequat. "
                + "Duis aute irure dolor in reprehenderit in voluptate velit "
                + "esse cillum dolore eu fixes "
                + "<a href=\"http://www.example.com?bug=132469187\" rel=\"noreferrer\" target=\"_blank\">132469187</a>"
                + " fugiat nulla pariatur. Excepteur sint "
                + "occaecat bug6478abc cupidatat non proident, sunt in culpa qui officia "
                + "deserunt mollit anim id est laborum.";
        String expected2
                = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
                + "sed do eiusmod tempor incididunt as per 12345698 ut labore et dolore magna "
                + "aliqua. "
                + "<a href=\"http://www.other-example.com?bug=3333\" rel=\"noreferrer\" target=\"_blank\">bug3333fff</a>"
                + " Ut enim ad minim veniam, quis nostrud exercitation "
                + "ullamco laboris nisi ut aliquip ex ea introduced in 9791216541 commodo consequat. "
                + "Duis aute irure dolor in reprehenderit in voluptate velit "
                + "esse cillum dolore eu fixes 132469187 fugiat nulla pariatur. Excepteur sint "
                + "occaecat "
                + "<a href=\"http://www.other-example.com?bug=6478\" rel=\"noreferrer\" target=\"_blank\">bug6478abc</a>"
                + " cupidatat non proident, sunt in culpa qui officia "
                + "deserunt mollit anim id est laborum.";

        assertEquals(expected, Util.linkifyPattern(text, Pattern.compile("\\b([0-9]{8,})\\b"), "$1", "http://www.example.com?bug=$1"));
        assertEquals(expected2, Util.linkifyPattern(text, Pattern.compile("\\b(bug([0-9]{4})\\w{3})\\b"), "$1",
                "http://www.other-example.com?bug=$2"));
    }

    @Test
    void testCompleteUrl() {
        HttpServletRequest req = new DummyHttpServletRequest() {
            @Override
            public int getServerPort() {
                return 8080;
            }

            @Override
            public String getServerName() {
                return "www.example.com";
            }

            @Override
            public String getScheme() {
                return "http";
            }

            @Override
            public StringBuffer getRequestURL() {
                return new StringBuffer(getScheme() + "://" + getServerName() + ':' + getServerPort() + "/source/location/undefined");
            }
        };

        // Absolute including hostname.
        assertEquals("http://opengrok.com/user=", Util.completeUrl("http://opengrok.com/user=", req));
        assertEquals("http://opengrok.cz.grok.com/user=", Util.completeUrl("http://opengrok.cz.grok.com/user=", req));
        assertEquals("http://opengrok.com/user=123&id=", Util.completeUrl("http://opengrok.com/user=123&id=", req));

        // Absolute/relative without the hostname.
        assertEquals("http://www.example.com:8080/cgi-bin/user=", Util.completeUrl("/cgi-bin/user=", req));
        assertEquals("http://www.example.com:8080/cgi-bin/user=123&id=", Util.completeUrl("/cgi-bin/user=123&id=", req));
        assertEquals("http://www.example.com:8080/source/location/undefined/cgi-bin/user=", Util.completeUrl("cgi-bin/user=", req));
        assertEquals("http://www.example.com:8080/source/location/undefined/cgi-bin/user=123&id=", Util.completeUrl("cgi-bin/user=123&id=", req));

        assertEquals("http://www.example.com:8080/source/location/undefined", Util.completeUrl("", req));

        // Escaping should work.
        assertEquals("http://www.example.com:8080/cgi-%22bin/user=123&id=", Util.completeUrl("/cgi-\"bin/user=123&id=", req));
    }

    @Test
    void getQueryParamsNullTest() {
        assertThrows(IllegalArgumentException.class, () -> Util.getQueryParams(null));
    }

    @Test
    void getQueryParamsEmptyTest() throws MalformedURLException {
        URL url = new URL("http://test.com/test");
        assertTrue(Util.getQueryParams(url).isEmpty());
    }

    @Test
    void getQueryParamsEmptyTest2() throws MalformedURLException {
        URL url = new URL("http://test.com/test?");
        assertTrue(Util.getQueryParams(url).isEmpty());
    }

    @Test
    void getQueryParamsSingleTest() throws MalformedURLException {
        URL url = new URL("http://test.com?param1=value1");
        Map<String, List<String>> params = Util.getQueryParams(url);

        assertEquals(1, params.size());

        assertThat(params.get("param1"), contains("value1"));
    }

    @Test
    void getQueryParamsMultipleTest() throws MalformedURLException {
        URL url = new URL("http://test.com?param1=value1&param2=value2");
        Map<String, List<String>> params = Util.getQueryParams(url);

        assertEquals(2, params.size());

        assertThat(params.get("param1"), contains("value1"));
        assertThat(params.get("param2"), contains("value2"));
    }

    @Test
    void getQueryParamsMultipleSameTest() throws MalformedURLException {
        URL url = new URL("http://test.com?param1=value1&param1=value2");
        Map<String, List<String>> params = Util.getQueryParams(url);

        assertEquals(1, params.size());

        assertThat(params.get("param1"), contains("value1", "value2"));
    }

    @Test
    void getQueryParamsEncodedTest() throws MalformedURLException {
        URL url = new URL("http://test.com?param1=%3Fvalue%3F");
        Map<String, List<String>> params = Util.getQueryParams(url);

        assertEquals(1, params.size());

        assertThat(params.get("param1"), contains("?value?"));
    }

    @Test
    void getQueryParamsEmptyValueTest() throws MalformedURLException {
        URL url = new URL("http://test.com?param1=");

        Map<String, List<String>> params = Util.getQueryParams(url);

        assertThat(params.get("param1"), contains(""));
    }

    @Test
    void getQueryParamsEmptyAndNormalValuesCombinedTest() throws MalformedURLException {
        URL url = new URL("http://test.com?param1=value1&param2=&param3&param4=value4");

        Map<String, List<String>> params = Util.getQueryParams(url);

        assertThat(params.get("param1"), contains("value1"));
        assertThat(params.get("param2"), contains(""));
        assertTrue(params.containsKey("param3"));
        assertThat(params.get("param4"), contains("value4"));
    }

    /**
     * Test {@link Util#writeHAD(Writer, String, String)} for a file path that does not map to any repository.
     * @throws Exception on error
     */
    @Test
    void testWriteHADNonexistentFile() throws Exception {
        StringWriter writer = new StringWriter();
        String filePath = "/nonexistent/file.c";
        Util.writeHAD(writer, "/source", filePath);
        String output = writer.toString();
        assertEquals("<td class=\"q\"><a href=\"/source/download/nonexistent/file.c\" title=\"Download\">D</a></td>",
                output);
    }

    /**
     * Test {@link Util#writeHAD(Writer, String, String)} for a file paths that correspond to a repository
     * with history enabled and disabled.
     * @throws Exception on error
     */
    @EnabledForRepository(MERCURIAL)
    @Test
    void testWriteHAD() throws Exception {
        TestRepository repository = new TestRepository();
        repository.create(UtilTest.class.getResource("/repositories"));

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        env.setHistoryEnabled(true);

        // The projects have to be added first so that prepareIndexer() can use their configuration.
        Project proj = new Project("mercurial", "/mercurial");
        proj.setHistoryEnabled(false);
        env.getProjects().clear();
        env.getProjects().put("mercurial", proj);
        proj = new Project("git", "/git");
        env.getProjects().put("git", proj);

        HistoryGuru.getInstance().clear();
        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                null, // subFiles - not needed since we don't list files
                null); // repositories - not needed when not refreshing history
        env.generateProjectRepositoriesMap();

        StringWriter writer = new StringWriter();
        String filePath = "/git/main.c";
        Util.writeHAD(writer, "/source", filePath);
        String output = writer.toString();
        assertEquals("<td class=\"q\"><a href=\"/source/history/git/main.c\" title=\"History\">H</a> " +
                        "<a href=\"/source/xref/git/main.c?a=true\" title=\"Annotate\">A</a> " +
                        "<a href=\"/source/download/git/main.c\" title=\"Download\">D</a></td>",
                output);

        writer = new StringWriter();
        filePath = "/mercurial/main.c";
        Util.writeHAD(writer, "/source", filePath);
        output = writer.toString();
        assertEquals("<td class=\"q\"> <a href=\"/source/xref/mercurial/main.c?a=true\" title=\"Annotate\">A</a> " +
                        "<a href=\"/source/download/mercurial/main.c\" title=\"Download\">D</a></td>",
                output);
    }
}
