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
 * Copyright (c) 2007, 2026, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.history.AnnotationData;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.w3c.dom.Element;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    static Stream<Arguments> htmlizeArguments() {
        return Stream.of(
                Arguments.of("This is a test", "This is a test"),
                Arguments.of("Newline\nshould become <br/>", "Newline<br/>should become &lt;br/&gt;"),
                Arguments.of("Open & Grok", "Open &amp; Grok"),
                // Supplementary characters are escaped as code points, not separate UTF-16 surrogates.
                Arguments.of("readme\uD83D\uDE00.txt", "readme&#128512;.txt"),
                Arguments.of("readme\uD83D.txt", "readme&#65533;.txt"),
                Arguments.of("readme\uDE00.txt", "readme&#65533;.txt"),
                Arguments.of("&amp;&lt;&gt;", "&amp;amp;&amp;lt;&amp;gt;"));
    }

    @ParameterizedTest
    @MethodSource("htmlizeArguments")
    void htmlize(String input, String expected) throws IOException {
        assertEquals(expected, Util.htmlize(input));
        StringBuilder sb = new StringBuilder();
        Util.htmlize(input, sb);
        assertEquals(expected, sb.toString());
    }

    @Test
    void htmlizeCharacterArray() throws IOException {
        char[] name = "readme\uD83D\uDE00.txt".toCharArray();
        StringBuilder result = new StringBuilder();
        Util.htmlize(name, name.length, result);
        assertEquals("readme&#128512;.txt", result.toString());

        result.setLength(0);
        Util.htmlize(name, 7, result);
        assertEquals("readme&#65533;", result.toString());
    }

    static Stream<Arguments> htmlizeCodePointArguments() {
        return Stream.of(
                Arguments.of((int) 'a', false, "a"),
                Arguments.of((int) '<', false, "&lt;"),
                Arguments.of((int) '\n', false, "<br/>"),
                Arguments.of((int) '\n', true, "\n"),
                Arguments.of(0x1F600, false, "&#128512;"),
                Arguments.of(0xD83D, false, "&#65533;"));
    }

    @ParameterizedTest
    @MethodSource("htmlizeCodePointArguments")
    void htmlizeCodePoint(int codePoint, boolean pre, String expected) {
        assertEquals(expected, Util.htmlize(codePoint, pre));
    }

    static Stream<Arguments> breadcrumbPathArguments() {
        return Stream.of(
                Arguments.of("/root/", null, null),
                Arguments.of("/root/", "", ""),
                Arguments.of("/root/", "x", "<a href=\"/root/x\">x</a>"),
                Arguments.of("/root/", "xx", "<a href=\"/root/xx\">xx</a>"),
                // Parent directories have a trailing slash in the link.
                Arguments.of("/r/", "a/b", "<a href=\"/r/a/\">a</a>/<a href=\"/r/a/b\">b</a>"),
                Arguments.of("/r/", "a/b/", "<a href=\"/r/a/\">a</a>/<a href=\"/r/a/b/\">b</a>/"));
    }

    @ParameterizedTest
    @MethodSource("breadcrumbPathArguments")
    void breadcrumbPath(String prefix, String path, String expected) {
        assertEquals(expected, Util.breadcrumbPath(prefix, path));
    }

    static Stream<Arguments> breadcrumbPathWithSeparatorArguments() {
        return Stream.of(Arguments.of("/r/", "java.lang.String", '.',
                "<a href=\"/r/java/\">java</a>."
                        + "<a href=\"/r/java/lang/\">lang</a>."
                        + "<a href=\"/r/java/lang/String\">String</a>"));
    }

    @ParameterizedTest
    @MethodSource("breadcrumbPathWithSeparatorArguments")
    void breadcrumbPathWithSeparator(String prefix, String path, char separator, String expected) {
        assertEquals(expected, Util.breadcrumbPath(prefix, path, separator));
    }

    static Stream<Arguments> breadcrumbPathWithOptionsArguments() {
        return Stream.of(
                Arguments.of("/root/", "xx", '/', "&project=y", false,
                        "<a href=\"/root/xx&project=y\">xx</a>"),
                // Compact paths retain the leading slash but have no link for the virtual root.
                Arguments.of("/root/", "../xx", '/', "&project=y", true,
                        "/<a href=\"/root//xx&project=y\">xx</a>"),
                Arguments.of("/r/", "../a/b/../c//d", '/', "", true,
                        "/<a href=\"/r//a/\">a</a>/"
                                + "<a href=\"/r//a/c/\">c</a>/"
                                + "<a href=\"/r//a/c/d\">d</a>"),
                // Path components are URI encoded in links and HTML escaped in labels.
                Arguments.of("/root/", "foo/bar>", '/', "&project=y", true,
                        "<a href=\"/root/foo/&project=y\">foo</a>/"
                                + "<a href=\"/root/foo/bar%3E&project=y\">bar&gt;</a>"));
    }

    @ParameterizedTest
    @MethodSource("breadcrumbPathWithOptionsArguments")
    void breadcrumbPathWithOptions(String prefix, String path, char separator,
            String postfix, boolean compact, String expected) {
        assertEquals(expected, Util.breadcrumbPath(prefix, path, separator, postfix, compact));
    }

    static Stream<Arguments> readableSizeArguments() {
        return Stream.of(
                Arguments.of(0L, "0 "),
                Arguments.of(1L, "1 "),
                Arguments.of(-1L, "-1 "),
                Arguments.of(1000L, "1,000 "),
                Arguments.of(1024L, "1 KiB"),
                Arguments.of(2500L, "2.4 KiB"),
                Arguments.of(1474560L, "<b>1.4 MiB</b>"),
                Arguments.of(3758489600L, "<b>3.5 GiB</b>"),
                Arguments.of(Long.MAX_VALUE, "<b>8,589,934,592 GiB</b>"));
    }

    @ParameterizedTest
    @MethodSource("readableSizeArguments")
    void readableSize(long size, String expected) {
        assertEquals(expected, Util.readableSize(size));
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
        assertEquals("\u0000etc\u0000passwd\u0000date", Util.path2uid("/etc/passwd", "date"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void fixPathIfWindows() {
        assertEquals("/var/opengrok", Util.fixPathIfWindows("\\var\\opengrok"));
    }

    @Test
    void uid2url() {
        assertEquals("/etc/passwd", Util.uid2url(Util.path2uid("/etc/passwd", "date")));
    }

    static Stream<Arguments> uriEncodeArguments() {
        return Stream.of(
                Arguments.of((String) null, ""),
                Arguments.of("", ""),
                Arguments.of("a b", "a+b"),
                Arguments.of("a#b", "a%23b"),
                Arguments.of("a/b", "a%2Fb"),
                Arguments.of("README.txt", "README.txt"));
    }

    @ParameterizedTest
    @MethodSource("uriEncodeArguments")
    void testUriEncode(String input, String expected) {
        assertEquals(expected, Util.uriEncode(input));
    }

    static Stream<Arguments> uriEncodePathArguments() {
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of("/", "/"),
                Arguments.of("a", "a"),
                Arguments.of("\t", "%09"),
                Arguments.of("a+b", "a%2Bb"),
                Arguments.of("a b", "a%20b"),
                Arguments.of("/a//x/yz/##/ / ?", "/a//x/yz/%23%23/%20/%20%3F"),
                Arguments.of("foo::bar::test.js", "foo%3A%3Abar%3A%3Atest.js"),
                Arguments.of("bl\u00E5b\u00E6rsyltet\u00F8y", "bl%C3%A5b%C3%A6rsyltet%C3%B8y"));
    }

    @ParameterizedTest
    @MethodSource("uriEncodePathArguments")
    void testUriEncodePath(String input, String expected) {
        assertEquals(expected, Util.uriEncodePath(input));
    }

    static Stream<Arguments> formQuoteEscapeArguments() {
        return Stream.of(
                Arguments.of(null, ""),
                Arguments.of("abc", "abc"),
                Arguments.of("\"abc\"", "&quot;abc&quot;"),
                Arguments.of("&aring;", "&amp;aring;"),
                Arguments.of("line\n\"<&", "line\n&quot;<&amp;"));
    }

    @ParameterizedTest
    @MethodSource("formQuoteEscapeArguments")
    void formQuoteEscape(String input, String expected) {
        assertEquals(expected, Util.formQuoteEscape(input));
    }

    static Stream<Arguments> difflineArguments() {
        return Stream.of(
                Arguments.of(
                        "if (a < b && foo < bar && c > d)",
                        "if (a < b && foo > bar && c > d)",
                        "if (a &lt; b &amp;&amp; foo <span class=\"d\">&lt;</span> bar &amp;&amp; c &gt; d)",
                        "if (a &lt; b &amp;&amp; foo <span class=\"a\">&gt;</span> bar &amp;&amp; c &gt; d)"),
                Arguments.of(
                        "foo << 1",
                        "foo >> 1",
                        "foo <span class=\"d\">&lt;&lt;</span> 1",
                        "foo <span class=\"a\">&gt;&gt;</span> 1"),
                Arguments.of(
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
                                + "</span>d_req_time, d_req_mil, h_resp_time, h_resp_mil) &quot;"),
                Arguments.of(
                        "\"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\", values);",
                        "\"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\", values);",
                        "&quot;VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)&quot;, values);",
                        "&quot;VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?<span "
                                + "class=\"a\">, ?</span>)&quot;, values);"),
                Arguments.of(
                        "char    *config_list = NULL;",
                        "char    **config_list = NULL;",
                        "char    *config_list = NULL;",
                        "char    *<span class=\"a\">*</span>config_list = NULL;"),
                Arguments.of(
                        "char    **config_list = NULL;",
                        "char    *config_list = NULL;",
                        "char    *<span class=\"d\">*</span>config_list = NULL;",
                        "char    *config_list = NULL;"),
                Arguments.of(
                        "* An error occured or there is non-numeric stuff at the end",
                        "* An error occurred or there is non-numeric stuff at the end",
                        "* An error occured or there is non-numeric stuff at the end",
                        "* An error occur<span class=\"a\">r</span>ed or there is "
                                + "non-numeric stuff at the end"));
    }

    @ParameterizedTest
    @MethodSource("difflineArguments")
    void diffline(String left, String right, String expectedLeft, String expectedRight) {
        String[] actual = Util.diffline(new StringBuilder(left), new StringBuilder(right));
        assertEquals(expectedLeft, actual[0]);
        assertEquals(expectedRight, actual[1]);
    }

    static Stream<Arguments> encodeArguments() {
        return Stream.of(
                Arguments.of("Test <code>title</code>", "Test&nbsp;&#60;code&#62;title&#60;/code&#62;"),
                Arguments.of("ahoj", "ahoj"),
                Arguments.of("<>|&\"'", "&#60;&#62;|&#38;&#34;&#39;"),
                Arguments.of("tab\ttab", "tab&nbsp;&nbsp;&nbsp;&nbsp;tab"),
                Arguments.of("multi\nline\t\nstring",
                        "multi&lt;br/&gt;line&nbsp;&nbsp;&nbsp;&nbsp;&lt;br/&gt;string"));
    }

    @ParameterizedTest
    @MethodSource("encodeArguments")
    void testEncode(String input, String expected) {
        assertEquals(expected, Util.encode(input));
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

    static Stream<Arguments> jsStringLiteralArguments() {
        return Stream.of(
                Arguments.of("\"abc\\n\\r\\\"\\\\\"", "abc\n\r\"\\"),
                Arguments.of("\"\\u003cfoo\\u003e\"", "<foo>"),
                Arguments.of("\"café 😀\"", "café 😀"),
                Arguments.of("\"\\u003c/script\\u003e\\u0026\"", "</script>&"),
                Arguments.of("\"\\u0009\\u2028\\u2029\"", "\t\u2028\u2029"));
    }

    @ParameterizedTest
    @MethodSource("jsStringLiteralArguments")
    void jsStringLiteral(String expected, String input) {
        assertEquals(expected, Util.jsStringLiteral(input));
    }

    static Stream<Arguments> stripPathPrefixArguments() {
        return Stream.of(
                Arguments.of("/", "/", "/"),
                Arguments.of("/abc", "/abc", "/abc"),
                Arguments.of("/abc", "/abc/", "/abc/"),
                Arguments.of("/abc/", "/abc", "/abc"),
                Arguments.of("/abc/", "/abc/", "/abc/"),
                Arguments.of("/", "/abc", "abc"),
                Arguments.of("/", "/abc/def", "abc/def"),
                Arguments.of("/abc", "/abc/def", "def"),
                Arguments.of("/abc/", "/abc/def", "def"),
                Arguments.of("/abc", "/abcdef", "/abcdef"),
                Arguments.of("/abc/", "/abcdef", "/abcdef"),
                Arguments.of("/abc", "/abc/def/ghi", "def/ghi"),
                Arguments.of("/abc/", "/abc/def/ghi", "def/ghi"));
    }

    @ParameterizedTest
    @MethodSource("stripPathPrefixArguments")
    void stripPathPrefix(String prefix, String fullPath, String expected) {
        assertEquals(expected, Util.stripPathPrefix(prefix, fullPath));
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

    static Stream<Arguments> isUrlArguments() {
        return Stream.of(
                Arguments.of("http://www.example.com", true),
                Arguments.of("http://example.com", true),
                Arguments.of("https://example.com", true),
                Arguments.of("https://www.example.com", true),
                Arguments.of("http://www.example.com?param=1&param2=2", true),
                Arguments.of("http://www.example.com/other/page", true),
                Arguments.of("https://www.example.com?param=1&param2=2", true),
                Arguments.of("https://www.example.com/other/page", true),
                Arguments.of("http://www.example.com:80/other/page", true),
                Arguments.of("http://www.example.com:8080/other/page", true),
                Arguments.of("https://www.example.com:80/other/page", true),
                Arguments.of("https://www.example.com:8080/other/page", true),
                Arguments.of("git@github.com:OpenGrok/OpenGrok", false),
                Arguments.of("hg@mercurial.com:OpenGrok/OpenGrok", false),
                Arguments.of("ssh://git@github.com:OpenGrok/OpenGrok", false),
                Arguments.of("ldap://example.com/OpenGrok/OpenGrok", false),
                Arguments.of("smtp://example.com/OpenGrok/OpenGrok", false));
    }

    @ParameterizedTest
    @MethodSource("isUrlArguments")
    void testIsUrl(String url, boolean expected) {
        assertEquals(expected, Util.isHttpUri(url));
    }

    static Stream<Arguments> redactUriArguments() {
        return Stream.of(
                Arguments.of("/foo/bar", "/foo/bar"),
                Arguments.of("http://foo/bar?r=xxx", "http://foo/bar?r=xxx"),
                Arguments.of("http://user@foo/bar?r=xxx",
                        "http://" + Util.REDACTED_USER_INFO + "@foo/bar?r=xxx"),
                Arguments.of("http://user:pass@foo/bar?r=xxx",
                        "http://" + Util.REDACTED_USER_INFO + "@foo/bar?r=xxx"));
    }

    @ParameterizedTest
    @MethodSource("redactUriArguments")
    void testRedactUri(String url, String expected) {
        assertEquals(expected, Util.redactUri(url));
    }

    static Stream<Arguments> linkArguments() {
        return Stream.of(
                Arguments.of("http://www.example.com", "http://www.example.com", false),
                Arguments.of("http://www.example.com", "http://www.example.com", true),
                Arguments.of("https://example.com", "https://example.com", false),
                Arguments.of("https://example.com", "https://example.com", true),
                Arguments.of("http://www.example.com?param=1&param2=2",
                        "http://www.example.com?param=1&amp;param2=2", false),
                Arguments.of("http://www.example.com?param=1&param2=2",
                        "http://www.example.com?param=1&amp;param2=2", true),
                Arguments.of("https://www.example.com:8080/other/page", "https://www.example.com:8080/other/page", false),
                Arguments.of("https://www.example.com:8080/other/page", "https://www.example.com:8080/other/page", true),
                Arguments.of("http://www.example.com?url=xasw&beta=gama",
                        "http://www.example.com?url=xasw&amp;beta=gama", false),
                Arguments.of("http://www.example.com?url=xasw&beta=gama",
                        "http://www.example.com?url=xasw&amp;beta=gama", true),
                Arguments.of("https://example.com/repo?a=1&copy;=2", "https://example.com/repo?a=1&amp;copy;=2", false),
                Arguments.of("https://example.com/repo?a=1&copy;=2", "https://example.com/repo?a=1&amp;copy;=2", true),
                Arguments.of("https://example.com/repo?value=&amp;", "https://example.com/repo?value=&amp;amp;", false),
                Arguments.of("https://example.com/repo?value=&amp;", "https://example.com/repo?value=&amp;amp;", true),
                Arguments.of("https://example.com/café?value='%22'",
                        "https://example.com/caf&#233;?value=&apos;%22&apos;", false),
                Arguments.of("https://example.com/café?value='%22'",
                        "https://example.com/caf&#233;?value=&apos;%22&apos;", true));
    }

    @ParameterizedTest(name = "{index}: {0}, newTab={2}")
    @MethodSource("linkArguments")
    void testLinkify(String url, String escapedUrl, boolean newTab) throws Exception {
        String tabAttributes = newTab ? " rel=\"noreferrer\" target=\"_blank\"" : "";
        String expected = "<a href=\"" + escapedUrl + "\"" + tabAttributes + " title=\"Link to "
                + escapedUrl + "\">" + escapedUrl + "</a>";
        String link = Util.linkify(url, newTab);
        assertEquals(expected, link);
        if (newTab) {
            assertEquals(expected, Util.linkify(url));
        }

        Element anchor = parseLink(link);
        assertEquals(url, anchor.getAttribute("href"));
        assertEquals("Link to " + url, anchor.getAttribute("title"));
        assertEquals(url, anchor.getTextContent());
    }

    static Stream<Arguments> linkifyPlainTextArguments() {
        return Stream.of(
                Arguments.of("git@github.com:OpenGrok/OpenGrok", "git@github.com:OpenGrok/OpenGrok"),
                Arguments.of("hg@mercurial.com:OpenGrok/OpenGrok", "hg@mercurial.com:OpenGrok/OpenGrok"),
                Arguments.of("ssh://git@github.com:OpenGrok/OpenGrok", "ssh://git@github.com:OpenGrok/OpenGrok"),
                Arguments.of("ldap://example.com/OpenGrok/OpenGrok", "ldap://example.com/OpenGrok/OpenGrok"),
                Arguments.of("smtp://example.com/OpenGrok/OpenGrok", "smtp://example.com/OpenGrok/OpenGrok"),
                Arguments.of("just some crazy text", "just some crazy text"),
                Arguments.of("<one> & \"two\"", "&lt;one&gt; &amp; &quot;two&quot;"),
                Arguments.of("http://example.com/a\"b", "http://example.com/a&quot;b"));
    }

    @ParameterizedTest
    @MethodSource("linkifyPlainTextArguments")
    void testLinkifyPlainText(String text, String expected) {
        assertEquals(expected, Util.linkify(text));
        assertEquals(expected, Util.linkify(text, false));
        assertEquals(expected, Util.linkify(text, true));
    }

    @ParameterizedTest(name = "{index}: {0}, newTab={2}")
    @MethodSource("linkArguments")
    void testBuildLink(String url, String escapedUrl, boolean newTab) throws Exception {
        String tabAttributes = newTab ? " rel=\"noreferrer\" target=\"_blank\"" : "";
        String expected = "<a href=\"" + escapedUrl + "\"" + tabAttributes + ">link</a>";
        String link = Util.buildLink("link", url, newTab);
        assertEquals(expected, link);
        if (!newTab) {
            assertEquals(expected, Util.buildLink("link", url));
        }

        Element anchor = parseLink(link);
        assertEquals(url, anchor.getAttribute("href"));
        assertEquals("link", anchor.getTextContent());
    }

    static Stream<Arguments> buildLinkAttributesArguments() {
        return Stream.of(
                Arguments.of("Some important title", "Some important title"),
                Arguments.of("<one> & \"two\" 'three' café 😀",
                        "&lt;one&gt; &amp; &quot;two&quot; &apos;three&apos; caf&#233; &#128512;"),
                Arguments.of("&amp; &copy;", "&amp;amp; &amp;copy;"));
    }

    @ParameterizedTest
    @MethodSource("buildLinkAttributesArguments")
    void testBuildLinkAttributes(String text, String escapedText) throws Exception {
        Map<String, String> attrs = new TreeMap<>();
        attrs.put("href", "https://www.example.com/abcd/acbd");
        attrs.put("title", text);
        attrs.put("data-id", "123456");

        String link = Util.buildLink(text, attrs);
        assertEquals("<a data-id=\"123456\" href=\"https://www.example.com/abcd/acbd\" title=\""
                + escapedText + "\">" + escapedText + "</a>", link);
        Element anchor = parseLink(link);
        assertEquals(text, anchor.getAttribute("title"));
        assertEquals(text, anchor.getTextContent());
        assertEquals(3, anchor.getAttributes().getLength());
    }

    private static Element parseLink(String link) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(link.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    @Test
    void testBuildLinkInvalidUrl1() {
        assertThrows(IllegalArgumentException.class, () -> Util.buildLink("link", "www.example.com")); // invalid protocol
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

        assertEquals(expected, Util.linkifyPattern(text, Pattern.compile("\\b([0-9]{8,})\\b"), "http://www.example.com?bug="));
    }

    /**
     * Matched pattern should be properly encoded in the resulting HTML.
     */
    @Test
    void testLinkifyPatternEscape() {
        final String text = "foo bug <123456> bar bug 777";
        final String expected = "foo bug <a href=\"http://www.example.com?bug=%3C123456%3E\" " +
                "rel=\"noreferrer\" target=\"_blank\">&lt;123456&gt;</a> bar " +
                "bug <a href=\"http://www.example.com?bug=777\" rel=\"noreferrer\" target=\"_blank\">777</a>";

        assertEquals(expected,
                Util.linkifyPattern(text, Pattern.compile("[ \\t]+([0-9<>]{3,})[ \\t]*"), "http://www.example.com?bug="));
    }

    @Test
    void testLinkifyPatternNoGroup() {
        final String text = "foo bug <123456> bar bug 777";

        assertEquals(text,
                Util.linkifyPattern(text, Pattern.compile("[0-9]{3,}"), "http://www.example.com?bug="));
    }

    static Stream<Arguments> completeUrlArguments() {
        return Stream.of(
                // Absolute URLs retain their hostname.
                Arguments.of("http://example.com/user=", "http://example.com/user="),
                Arguments.of("http://opengrok.cz.example.com/user=", "http://opengrok.cz.example.com/user="),
                Arguments.of("http://example.com/user=123&id=", "http://example.com/user=123&id="),
                // Root-relative and relative URLs use the request URL.
                Arguments.of("/cgi-bin/user=", "http://www.example.com:8080/cgi-bin/user="),
                Arguments.of("/cgi-bin/user=123&id=", "http://www.example.com:8080/cgi-bin/user=123&id="),
                Arguments.of("cgi-bin/user=", "http://www.example.com:8080/source/location/undefined/cgi-bin/user="),
                Arguments.of("cgi-bin/user=123&id=",
                        "http://www.example.com:8080/source/location/undefined/cgi-bin/user=123&id="),
                Arguments.of("", "http://www.example.com:8080/source/location/undefined"),
                Arguments.of("/cgi-\"bin/user=123&id=", "http://www.example.com:8080/cgi-%22bin/user=123&id="));
    }

    @ParameterizedTest
    @MethodSource("completeUrlArguments")
    void testCompleteUrl(String url, String expected) {
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

        assertEquals(expected, Util.completeUrl(url, req));
    }

    @Test
    void getQueryParamsNullTest() {
        assertThrows(IllegalArgumentException.class, () -> Util.getQueryParams(null));
    }

    @Test
    void getQueryParamsEmptyTest() throws MalformedURLException, URISyntaxException {
        URL url = new URI("http://test.com/test").toURL();
        assertTrue(Util.getQueryParams(url).isEmpty());
    }

    @Test
    void getQueryParamsEmptyTest2() throws MalformedURLException, URISyntaxException {
        URL url = new URI("http://test.com/test?").toURL();
        assertTrue(Util.getQueryParams(url).isEmpty());
    }

    @Test
    void getQueryParamsSingleTest() throws MalformedURLException, URISyntaxException {
        URL url = new URI("http://test.com?param1=value1").toURL();
        Map<String, List<String>> params = Util.getQueryParams(url);

        assertEquals(1, params.size());

        assertThat(params.get("param1"), contains("value1"));
    }

    @Test
    void getQueryParamsMultipleTest() throws MalformedURLException, URISyntaxException {
        URL url = new URI("http://test.com?param1=value1&param2=value2").toURL();
        Map<String, List<String>> params = Util.getQueryParams(url);

        assertEquals(2, params.size());

        assertThat(params.get("param1"), contains("value1"));
        assertThat(params.get("param2"), contains("value2"));
    }

    @Test
    void getQueryParamsMultipleSameTest() throws MalformedURLException, URISyntaxException {
        URL url = new URI("http://test.com?param1=value1&param1=value2").toURL();
        Map<String, List<String>> params = Util.getQueryParams(url);

        assertEquals(1, params.size());

        assertThat(params.get("param1"), contains("value1", "value2"));
    }

    @Test
    void getQueryParamsEncodedTest() throws MalformedURLException, URISyntaxException {
        URL url = new URI("http://test.com?param1=%3Fvalue%3F").toURL();
        Map<String, List<String>> params = Util.getQueryParams(url);

        assertEquals(1, params.size());

        assertThat(params.get("param1"), contains("?value?"));
    }

    @Test
    void getQueryParamsEmptyValueTest() throws MalformedURLException, URISyntaxException {
        URL url = new URI("http://test.com?param1=").toURL();

        Map<String, List<String>> params = Util.getQueryParams(url);

        assertThat(params.get("param1"), contains(""));
    }

    @Test
    void getQueryParamsEmptyAndNormalValuesCombinedTest() throws MalformedURLException, URISyntaxException {
        URL url = new URI("http://test.com?param1=value1&param2=&param3&param4=value4").toURL();

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

    @Test
    void testWriteHADEncodesPathExactlyOnce() throws Exception {
        StringWriter writer = new StringWriter();

        Util.writeHAD(writer, "/source", "/dir with space/file+%.c");

        assertEquals("<td class=\"q\"><a href=\"/source/download/dir%20with%20space/file%2B%25.c\" " +
                        "title=\"Download\">D</a></td>", writer.toString());
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
        URL repositoryURL = UtilTest.class.getResource("/repositories");
        assertNotNull(repositoryURL);
        repository.create(repositoryURL);

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWriteAnnotation(boolean enabled) throws IOException {
        StringWriter writer = new StringWriter();
        AnnotationData annotationData = new AnnotationData();
        final String rev = "searchRev";
        annotationData.addLine(rev, "author", enabled, "dispRev");
        Annotation annotation = new Annotation(annotationData);
        annotation.addDesc(rev, "description");
        Util.writeAnnotation(1, writer, annotation, null, null, "foo");
        String output = writer.toString();
        String expectedOutput;
        if (enabled) {
            expectedOutput = "<span class=\"blame\">" +
                    "<a class=\"r title-tooltip\" style=\"background-color: rgb(255, 191, 195)\" " +
                    "href=\"?a=true&amp;r=searchRev\" title=\"description\">dispRev</a>" +
                    "<a class=\"search\" href=\"/source/s?defs=&amp;refs=&amp;path=foo&amp;hist=&quot;searchRev&quot;&amp;type=\" " +
                    "title=\"Search history for this revision\">S</a><span class=\"a\">author</span></span>";
        } else {
            expectedOutput = "<span class=\"blame\">dispRev<span class=\"a\">author</span></span>";
        }
        assertEquals(expectedOutput, output);
    }
}
