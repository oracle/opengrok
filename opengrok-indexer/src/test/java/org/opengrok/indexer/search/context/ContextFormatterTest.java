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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            "morbi tristique senectus et netus.\n" +
            "Nam scelerisque odio at justo fringilla, eu aliquet sem commodo.\n" +
            "Duis aliquet non magna ac gravida. Aliquam erat volutpat. Proin\n" +
            "nec iaculis mauris.";

    private static final String DOC2 = "abc\n" +
            "def\n" +
            "ghi";

    @Test
    public void testLineMatchFormatted() {
        final String WORD = "gravida";
        int woff = DOC.indexOf(WORD);
        assertTrue(woff >= 0, WORD);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        // First, test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_0 =
                "<a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">" +
                "3</span> Mauris diam nisl, tincidunt nec <b>gravida</b> sit " +
                "amet, efficitur vitae</a><br/>\n";
        String ctx = res.toString();
        assertLinesEqual(DOCCTX_0, ctx, "format().toString()");

        // Second, test with contextCount==1
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_1 =
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#2\"><span class=\"l\">" +
                "2</span> Mauris vel tortor vel nisl efficitur fermentum nec vel" +
                " erat.</a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">" +
                "3</span> Mauris diam nisl, tincidunt nec <b>gravida</b> sit" +
                " amet, efficitur vitae</a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#4\"><span class=\"l\">" +
                "4</span> est. Sed aliquam non mi vel mattis:</a><br/></span>";
        ctx = res.toString();
        assertLinesEqual(DOCCTX_1, ctx, "format().toString()");
    }

    @Test
    public void testLinesSpanningMatchFormatted() {
        Passage p = new Passage();
        p.setStartOffset(0);
        p.setEndOffset(DOC2.length());
        p.addMatch(0, p.getEndOffset(), new BytesRef(DOC2), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        /*
         * We're using the entire document, but see how it behaves with
         * contextCount==1
         */
        ContextArgs args = new ContextArgs((short) 1, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC2);
        assertNotNull(res, "format() result");

        final String DOC2CTX =
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#1\"><span class=\"l\">" +
                "1</span> <b>abc</b></a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#2\"><span class=\"l\">" +
                "2</span> <b>def</b></a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">" +
                "3</span> <b>ghi</b></a><br/></span>";
        String ctx = res.toString();
        assertLinesEqual(DOC2CTX, ctx, "format().toString()");
    }

    @Test
    public void testDualElidedMatchFormatted() {
        final String WORD = "dignissim";
        int woff = DOC.indexOf(WORD);
        assertTrue(woff >= 0, WORD);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        // First, test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_0 = "<a class=\"s\" href=\"http://example.com#6\"><span class=\"l\">" +
                "6</span> &hellip;putate ipsum sed laoreet. Nam maximus libero" +
                " non ornare egestas. Aenean <b>dignissim</b> ipsum eu" +
                " rhoncus&hellip;</a><br/>\n";
        String ctx = res.toString();
        assertLinesEqual(DOCCTX_0, ctx, "format().toString()");

        // Second, test with contextCount==1
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_1 =
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#5\"><span class=\"l\">" +
                "5</span> </a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#6\"><span class=\"l\">" +
                "6</span> &hellip;putate ipsum sed laoreet. Nam maximus libero" +
                " non ornare egestas. Aenean <b>dignissim</b> ipsum eu" +
                " rhoncus&hellip;</a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#7\"><span class=\"l\">" +
                "7</span> </a><br/></span>";
        ctx = res.toString();
        assertLinesEqual(DOCCTX_1, ctx, "format().toString()");

        // Third, test with contextCount==1 and a line limit
        args = new ContextArgs((short) 1, (short) 2);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        fmt.setMoreUrl("http://example.com/more");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_2M =
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#5\"><span class=\"l\">" +
                "5</span> </a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#6\"><span class=\"l\">" +
                "6</span> &hellip;putate ipsum sed laoreet. Nam maximus libero" +
                " non ornare egestas. Aenean <b>dignissim</b> ipsum eu" +
                " rhoncus&hellip;</a><br/></span>" +
                "<a href=\"http://example.com/more\">[all &hellip;]</a><br/>";
        ctx = res.toString();
        assertLinesEqual(DOCCTX_2M, ctx, "format().toString()");
    }

    @Test
    public void testLeftElidedMatchFormatted() {
        final String WORD = "ultricies";
        int woff = DOC.indexOf(WORD);
        assertTrue(woff >= 0, WORD);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        // First, test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_0 =
                "<a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> &hellip;um sed laoreet. Nam " +
                "maximus libero non ornare egestas. Aenean " +
                "dignissim ipsum eu rhoncus <b>ultricies</b>.</a>" +
                "<br/>";
        String ctx = res.toString();
        assertLinesEqual(DOCCTX_0, ctx, "format().toString()");

        // Second, test with contextCount==1
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_1 =
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#5\"><span " +
                "class=\"l\">5</span> </a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> &hellip;um sed laoreet. Nam " +
                "maximus libero non ornare egestas. Aenean " +
                "dignissim ipsum eu rhoncus <b>ultricies</b>.</a>" +
                "<br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#7\"><span " +
                "class=\"l\">7</span> </a><br/></span>";
        ctx = res.toString();
        assertLinesEqual(DOCCTX_1, ctx, "format().toString()");

        // Third, test with contextCount==1 and a line limit
        args = new ContextArgs((short) 1, (short) 2);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        fmt.setMoreUrl("http://example.com/more");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_2M =
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#5\">" +
                "<span class=\"l\">5</span> </a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> &hellip;um sed laoreet. Nam " +
                "maximus libero non ornare egestas. Aenean " +
                "dignissim ipsum eu rhoncus <b>ultricies</b>.</a>" +
                "<br/></span>" +
                "<a href=\"http://example.com/more\">[all " +
                "&hellip;]</a><br/>";
        ctx = res.toString();
        assertLinesEqual(DOCCTX_2M, ctx, "format().toString()");
    }

    @Test
    public void testRightElidedMatchFormatted() {
        final String WORD = "Maecenas";
        int woff = DOC.indexOf(WORD);
        assertTrue(woff >= 0, WORD);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        // First, test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_0 = "<a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> ----<b>Maecenas</b> vitae " +
                "lacus velit varius vulputate ipsum sed laoreet. " +
                "Nam maximus libero non ornare eg&hellip;</a><br/>";
        String ctx = res.toString();
        assertLinesEqual(DOCCTX_0, ctx, "format().toString()");

        // Second, test with contextCount==1
        args = new ContextArgs((short) 1, (short) 10);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_1 =
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#5\"><span " +
                "class=\"l\">5</span> </a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> ----<b>Maecenas</b> vitae " +
                "lacus velit varius vulputate ipsum sed laoreet. " +
                "Nam maximus libero non ornare eg&hellip;</a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#7\"><span " +
                "class=\"l\">7</span> </a><br/></span>";
        ctx = res.toString();
        assertLinesEqual(DOCCTX_1, ctx, "format().toString()");

        // Third, test with contextCount==1 and a line limit
        args = new ContextArgs((short) 1, (short) 2);
        fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        fmt.setMoreUrl("http://example.com/more");
        res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOCCTX_2M =
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#5\"><span " +
                "class=\"l\">5</span> </a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#6\"><span " +
                "class=\"l\">6</span> ----<b>Maecenas</b> vitae " +
                "lacus velit varius vulputate ipsum sed laoreet. " +
                "Nam maximus libero non ornare eg&hellip;</a><br/></span>" +
                "<a href=\"http://example.com/more\">[all " +
                "&hellip;]</a><br/>\n";
        ctx = res.toString();
        assertLinesEqual(DOCCTX_2M, ctx, "format().toString()");
    }

    @Test
    public void testBoundsProblemFormatted() {
        final String PHRASE = "efficitur vitae";
        int phOff = DOC.indexOf(PHRASE);
        assertTrue(phOff >= 0, PHRASE);

        // Create a slightly-longer word of all '*'.
        final int LF_CHAR_COUNT = 1;
        final String STARS = String.join("", Collections.nCopies(
                PHRASE.length() + LF_CHAR_COUNT, "*"));

        Passage p = new Passage();
        p.setStartOffset(phOff);
        p.setEndOffset(phOff + STARS.length());
        p.addMatch(phOff, p.getEndOffset(), new BytesRef(STARS), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        // Test with contextCount==0
        ContextArgs args = new ContextArgs((short) 0, (short) 10);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object res = fmt.format(new Passage[] {p}, DOC);
        assertNotNull(res, "format() result");

        final String DOC_CTX_0 = "<a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">" +
                "3</span> Mauris diam nisl, tincidunt nec gravida sit" +
                " amet, <b>efficitur vitae</b></a><br/>\n";
        String ctx = res.toString();
        assertLinesEqual(DOC_CTX_0, ctx, "format().toString()");
    }

    @Test
    public void testRecalculatedContextLimit() {
        final String PHRASE1 = "efficitur fermentum";
        final String PHRASE2 = "varius vulputate";
        final String PHRASE3 = "justo fringilla";

        Passage passage1 = new Passage();
        int phraseOff1 = DOC.indexOf(PHRASE1);
        passage1.setStartOffset(phraseOff1);
        passage1.setEndOffset(phraseOff1 + PHRASE1.length());
        assertTrue(passage1.getStartOffset() >= 0 && passage1.getEndOffset() >
                passage1.getStartOffset(), "passage1 offsets are positive");
        passage1.addMatch(passage1.getStartOffset(), passage1.getEndOffset(), new BytesRef(PHRASE1), 1);

        Passage passage2 = new Passage();
        int phraseOff2 = DOC.indexOf(PHRASE2);
        passage2.setStartOffset(phraseOff2);
        passage2.setEndOffset(phraseOff2 + PHRASE2.length());
        assertTrue(passage2.getStartOffset() >= 0 && passage2.getEndOffset() >
                passage2.getStartOffset(), "passage2 offsets are positive");
        passage2.addMatch(passage2.getStartOffset(), passage2.getEndOffset(), new BytesRef(PHRASE2), 1);

        Passage passage3 = new Passage();
        int phraseOff3 = DOC.indexOf(PHRASE3);
        passage3.setStartOffset(phraseOff3);
        passage3.setEndOffset(phraseOff3 + PHRASE3.length());
        assertTrue(passage3.getStartOffset() >= 0 && passage3.getEndOffset() >
                passage3.getStartOffset(), "passage3 offsets are positive");
        passage3.addMatch(passage3.getStartOffset(), passage3.getEndOffset(), new BytesRef(PHRASE3), 1);

        // Test with non-zero context limit.
        ContextArgs args = new ContextArgs((short) 1, (short) 7);
        ContextFormatter fmt = new ContextFormatter(args);
        fmt.setUrl("http://example.com");
        Object formatted = fmt.format(new Passage[] {passage1, passage2, passage3}, DOC);
        assertNotNull(formatted, "format() result");

        final String DOC_CTX_0 = "<span class=\"xovl\">" +
                "<a class=\"s\" href=\"http://example.com#1\"><span class=\"l\">1</span>" +
                "     Lorem ipsum dolor sit amet, consectetur adipiscing elit.</a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#2\">" +
                "<span class=\"l\">2</span> Mauris vel tortor vel nisl " +
                "<b>efficitur fermentum</b> nec vel erat.</a><br/></span><span class=\"xovl\">" +
                "<a class=\"s\" href=\"http://example.com#3\"><span class=\"l\">3</span> " +
                "Mauris diam nisl, tincidunt nec gravida sit amet, efficitur vitae</a><br/></span>" +
                "<span class=\"ovl\"><a class=\"s\" href=\"http://example.com#5\">" +
                "<span class=\"l\">5</span> </a><br/></span><span class=\"xovl\">" +
                "<a class=\"s\" href=\"http://example.com#6\"><span class=\"l\">6</span> " +
                "----Maecenas vitae lacus velit <b>varius vulputate</b> ipsum sed laoreet. " +
                "Nam maximus libero non ornare eg&hellip;</a><br/></span>" +
                "<span class=\"xovl\"><a class=\"s\" href=\"http://example.com#7\">" +
                "<span class=\"l\">7</span> </a><br/></span>";
        assertLinesEqual(DOC_CTX_0, formatted.toString(), "format().toString()");
    }
}
