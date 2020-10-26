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
package org.opengrok.indexer.search.context;

import java.util.Collections;

import org.apache.lucene.search.uhighlight.Passage;
import org.apache.lucene.util.BytesRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opengrok.indexer.util.CustomAssertions.assertLinesEqual;

/**
 * Represents a container for tests of {@link ContextFormatter}.
 */
public class ContextFormatterTest {

    private static final String DOC = "    Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n" +
            "Mauris vel tortor vel nisl efficitur fermentum nec vel erat.\n" +
            "Mauris diam nisl, tincidunt nec gravida sit amet, efficitur vitae\n" +
            "est. Sed aliquam non mi vel mattis:\n" +
            "\n" +
            "----Maecenas vitae lacus velit varius vulputate ipsum sed laoreet. Nam maximus libero non ornare egestas." +
            " Aenean dignissim ipsum eu rhoncus ultricies.\n" +
            "\n" +
            "    Fusce pretium hendrerit dictum. Pellentesque habitant\n" +
            "morbi tristique senectus et netus.";

    private static final String DOC2 = "abc\n" +
            "def\n" +
            "ghi";

    @Test
    public void testLineMatchFormatted() {
        final String WORD = "gravida";
        int woff = DOC.indexOf(WORD);
        assertTrue(WORD, woff >= 0);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        // First, test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_0 =
                "<a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">" +
                        "3</span> Mauris diam nisl, tincidunt nec <b>gravida</b> sit" +
                        " amet, efficitur vitae</a><br/>\n";
        String ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_0, ctx);

        // Second, test with contextCount==1
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_1 = "<a class=\"s\" href=\"http://example.com#2\"><span class=\"l\">" +
                "2</span> Mauris vel tortor vel nisl efficitur fermentum nec vel" +
                " erat.</a><br/>" +
                "<a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">" +
                "3</span> Mauris diam nisl, tincidunt nec <b>gravida</b> sit" +
                " amet, efficitur vitae</a><br/>" +
                "<a class=\"s\" href=\"http://example.com#4\"><span class=\"l\">" +
                "4</span> est. Sed aliquam non mi vel mattis:</a><br/>";
        ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_1, ctx);
    }

    @Test
    public void testLinesSpanningMatchFormatted() {
        Passage p = new Passage();
        p.setStartOffset(0);
        p.setEndOffset(DOC2.length());
        p.addMatch(0, p.getEndOffset(), new BytesRef(DOC2), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        /**
         * We're using the entire document, but see how it behaves with
         * contextCount==1
         */
        ContextArgs args = new ContextArgs((short) 1, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC2);
        assertNotNull("format() result", res);

        final String DOC2CTX =
                "<a class=\"s\" href=\"http://example.com#1\"><span class=\"l\">" +
                        "1</span> <b>abc</b></a><br/>" +
                        "<a class=\"s\" href=\"http://example.com#2\"><span class=\"l\">" +
                        "2</span> <b>def</b></a><br/>" +
                        "<a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">" +
                        "3</span> <b>ghi</b></a><br/>";
        String ctx = res.toString();
        assertLinesEqual("format().toString()", DOC2CTX, ctx);
    }

    @Test
    public void testDualElidedMatchFormatted() {
        final String WORD = "dignissim";
        int woff = DOC.indexOf(WORD);
        assertTrue(WORD, woff >= 0);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        // First, test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_0 = "<a class=\"s\" href=\"http://example.com#6\"><span class=\"l\">" +
                "6</span> &hellip;putate ipsum sed laoreet. Nam maximus libero" +
                " non ornare egestas. Aenean <b>dignissim</b> ipsum eu" +
                " rhoncus&hellip;</a><br/>\n";
        String ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_0, ctx);

        // Second, test with contextCount==1
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_1 = "<a class=\"s\" href=\"http://example.com#5\"><span class=\"l\">" +
                "5</span> </a><br/>" +
                "<a class=\"s\" href=\"http://example.com#6\"><span class=\"l\">" +
                "6</span> &hellip;putate ipsum sed laoreet. Nam maximus libero" +
                " non ornare egestas. Aenean <b>dignissim</b> ipsum eu" +
                " rhoncus&hellip;</a><br/>" +
                "<a class=\"s\" href=\"http://example.com#7\"><span class=\"l\">" +
                "7</span> </a><br/>";
        ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_1, ctx);

        // Third, test with contextCount==1 and a line limit
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        fmt.setMoreLimit(2);
        fmt.setMoreUrl("http://example.com/more");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_2M = "<a class=\"s\" href=\"http://example.com#5\"><span class=\"l\">" +
                "5</span> </a><br/>" +
                "<a class=\"s\" href=\"http://example.com#6\"><span class=\"l\">" +
                "6</span> &hellip;putate ipsum sed laoreet. Nam maximus libero" +
                " non ornare egestas. Aenean <b>dignissim</b> ipsum eu" +
                " rhoncus&hellip;</a><br/>" +
                "<a href=\"http://example.com/more\">[all &hellip;]</a><br/>";
        ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_2M, ctx);
    }

    @Test
    public void testLeftElidedMatchFormatted() {
        final String WORD = "ultricies";
        int woff = DOC.indexOf(WORD);
        assertTrue(WORD, woff >= 0);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        // First, test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_0 =
                "<a class=\"s\" href=\"http://example.com#6\"><span " +
                        "class=\"l\">6</span> &hellip;um sed laoreet. Nam " +
                        "maximus libero non ornare egestas. Aenean " +
                        "dignissim ipsum eu rhoncus <b>ultricies</b>.</a>" +
                        "<br/>";
        String ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_0, ctx);

        // Second, test with contextCount==1
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_1 =
                "<a class=\"s\" href=\"http://example.com#5\"><span " +
                        "class=\"l\">5</span> </a><br/>" +
                        "<a class=\"s\" href=\"http://example.com#6\"><span " +
                        "class=\"l\">6</span> &hellip;um sed laoreet. Nam " +
                        "maximus libero non ornare egestas. Aenean " +
                        "dignissim ipsum eu rhoncus <b>ultricies</b>.</a>" +
                        "<br/>" +
                        "<a class=\"s\" href=\"http://example.com#7\"><span " +
                        "class=\"l\">7</span> </a><br/>";
        ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_1, ctx);

        // Third, test with contextCount==1 and a line limit
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        fmt.setMoreLimit(2);
        fmt.setMoreUrl("http://example.com/more");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_2M = "<a class=\"s\" href=\"http://example.com#5\">" +
                "<span class=\"l\">5</span> </a><br/>" +
                "<a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> &hellip;um sed laoreet. Nam " +
                "maximus libero non ornare egestas. Aenean " +
                "dignissim ipsum eu rhoncus <b>ultricies</b>.</a>" +
                "<br/><a href=\"http://example.com/more\">[all " +
                "&hellip;]</a><br/>";
        ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_2M, ctx);
    }

    @Test
    public void testRightElidedMatchFormatted() {
        final String WORD = "Maecenas";
        int woff = DOC.indexOf(WORD);
        assertTrue(WORD, woff >= 0);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        // First, test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_0 = "<a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> ----<b>Maecenas</b> vitae " +
                "lacus velit varius vulputate ipsum sed laoreet. " +
                "Nam maximus libero non ornare eg&hellip;</a><br/>";
        String ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_0, ctx);

        // Second, test with contextCount==1
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_1 =
                "<a class=\"s\" href=\"http://example.com#5\"><span " +
                        "class=\"l\">5</span> </a><br/>" +
                        "<a class=\"s\" href=\"http://example.com#6\"><span " +
                        "class=\"l\">6</span> ----<b>Maecenas</b> vitae " +
                        "lacus velit varius vulputate ipsum sed laoreet. " +
                        "Nam maximus libero non ornare eg&hellip;</a><br/>" +
                        "<a class=\"s\" href=\"http://example.com#7\"><span " +
                        "class=\"l\">7</span> </a><br/>";
        ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_1, ctx);

        // Third, test with contextCount==1 and a line limit
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        fmt.setMoreLimit(2);
        fmt.setMoreUrl("http://example.com/more");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOCCTX_2M = "<a class=\"s\" href=\"http://example.com#5\"><span " +
                "class=\"l\">5</span> </a><br/>" +
                "<a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> ----<b>Maecenas</b> vitae " +
                "lacus velit varius vulputate ipsum sed laoreet. " +
                "Nam maximus libero non ornare eg&hellip;</a><br/>" +
                "<a href=\"http://example.com/more\">[all " +
                "&hellip;]</a><br/>\n";
        ctx = res.toString();
        assertLinesEqual("format().toString()", DOCCTX_2M, ctx);
    }

    @Test
    public void testBoundsProblemFormatted() {
        final String PHRASE = "efficitur vitae";
        int phOff = DOC.indexOf(PHRASE);
        assertTrue(PHRASE, phOff >= 0);

        // Create a slightly-longer word of all '*'.
        final int LF_CHAR_COUNT = 1;
        final String STARS = String.join("", Collections.nCopies(
                PHRASE.length() + LF_CHAR_COUNT, "*"));

        Passage p = new Passage();
        p.setStartOffset(phOff);
        p.setEndOffset(phOff + STARS.length());
        p.addMatch(phOff, p.getEndOffset(), new BytesRef(STARS), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        // Test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull("format() result", res);

        final String DOC_CTX_0 = "<a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">" +
                "3</span> Mauris diam nisl, tincidunt nec gravida sit" +
                " amet, <b>efficitur vitae</b></a><br/>\n";
        String ctx = res.toString();
        assertLinesEqual("format().toString()", DOC_CTX_0, ctx);
    }
}
