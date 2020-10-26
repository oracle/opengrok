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

import java.util.SortedMap;

import org.apache.lucene.search.uhighlight.Passage;
import org.apache.lucene.util.BytesRef;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.util.SourceSplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Represents a container for tests of {@link PassageConverter} etc.
 */
public class PassageConverterTest {

    private static final String DOC = "    Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n" +
            "Mauris vel tortor vel nisl efficitur fermentum nec vel erat.\n" +
            "Mauris diam nisl, tincidunt nec gravida sit amet, efficitur vitae\n" +
            "est. Sed aliquam non mi vel mattis:\n" +
            "\n" +
            "    Maecenas vitae lacus velit varius vulputate ipsum sed laoreet. Nam maximus libero non ornare egestas." +
            " Aenean dignissim ipsum eu rhoncus ultricies.\n" +
            "\n" +
            "    Fusce pretium hendrerit dictum. Pellentesque habitant\n" +
            "morbi tristique senectus et netus.";

    private static final String DOC2 = "abc\ndef\nghi";

    private static SourceSplitter splitter;
    private static SourceSplitter splitter2;

    @BeforeClass
    public static void setUpClass() throws Exception {
        splitter = new SourceSplitter();
        splitter.reset(DOC);
        splitter2 = new SourceSplitter();
        splitter2.reset(DOC2);
    }

    @Test
    public void testOneWord() {
        final String WORD = "gravida";
        int woff = DOC.indexOf(WORD);
        assertTrue(WORD, woff >= 0);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter);

        assertEquals("linemap size()", 1, linemap.size());
        int lineno = linemap.firstKey();
        assertEquals("lineno", 2, lineno);

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull("get LineHighlight", lhi);
        assertEquals("getLelide()", 0, lhi.getLelide());
        assertEquals("getRelide()", 0, lhi.getRelide());
        assertEquals("countMarkups()", 1, lhi.countMarkups());

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("getLineStart()", 32, phi.getLineStart());
        assertEquals("getLineEnd()", 32 + WORD.length(), phi.getLineEnd());
    }

    @Test
    public void testOneWordElided() {
        final String WORD = "dignissim";
        int woff = DOC.indexOf(WORD);
        assertTrue(WORD, woff >= 0);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter);

        assertEquals("linemap size()", 1, linemap.size());
        int lineno = linemap.firstKey();
        assertEquals("lineno", 5, lineno);

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull("get LineHighlight", lhi);
        assertEquals("getLelide()", 41, lhi.getLelide());
        assertEquals("getRelide()", 139, lhi.getRelide());
        assertEquals("context width minus 2", 139 - 41,
                cvt.getArgs().getContextWidth() - 2);
        assertEquals("countMarkups()", 1, lhi.countMarkups());

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("getLineStart()", 113, phi.getLineStart());
        assertEquals("getLineEnd()", 113 + WORD.length(), phi.getLineEnd());
    }

    @Test
    public void testTwoWordsElided() {
        final String WORD1 = "Maecenas";
        int woff1 = DOC.indexOf(WORD1);
        assertTrue(WORD1, woff1 >= 0);

        final String WORD2 = "rhoncus";
        int woff2 = DOC.indexOf(WORD2);
        assertTrue(WORD2, woff2 >= 0);

        Passage p = new Passage();
        p.setStartOffset(woff1);
        p.setEndOffset(woff2 + WORD2.length());
        p.addMatch(woff1, woff1 + WORD1.length(), new BytesRef(WORD1), 1);
        p.addMatch(woff2, woff2 + WORD2.length(), new BytesRef(WORD2), 1);
        assertEquals("getNumMatches()", 2, p.getNumMatches());

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter);

        assertEquals("linemap size()", 1, linemap.size());
        int lineno = linemap.firstKey();
        assertEquals("lineno", 5, lineno);

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull("get LineHighlight", lhi);
        assertEquals("getLelide()", 4, lhi.getLelide());
        assertEquals("getRelide()", 102, lhi.getRelide());
        assertEquals("context width minus 2", 139 - 41,
                cvt.getArgs().getContextWidth() - 2);
        assertEquals("countMarkups()", 2, lhi.countMarkups());

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("0:getLineStart()", 4, phi.getLineStart());
        assertEquals("0:getLineEnd()", 4 + WORD1.length(), phi.getLineEnd());

        phi = lhi.getMarkup(1);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("1:getLineStart()", 132, phi.getLineStart());
        assertEquals("1:getLineEnd()", 132 + WORD2.length(), phi.getLineEnd());
    }

    @Test
    public void testLineSpanningMatch() {
        final String PHRASE = "elit.\nMauris";
        int poff = DOC.indexOf(PHRASE);
        assertTrue(PHRASE, poff >= 0);

        Passage p = new Passage();
        p.setStartOffset(poff);
        p.setEndOffset(poff + PHRASE.length());
        p.addMatch(poff, p.getEndOffset(), new BytesRef(PHRASE), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter);

        assertEquals("linemap size()", 2, linemap.size());
        int lineno = linemap.firstKey();
        assertEquals("first lineno", 0, lineno);
        assertTrue("linemap[1] exists", linemap.containsKey(1));

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull("get LineHighlight", lhi);
        assertEquals("getLelide()", 0, lhi.getLelide());
        assertEquals("getRelide()", 0, lhi.getRelide());
        assertEquals("countMarkups()", 1, lhi.countMarkups());

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("getLineStart()", 55, phi.getLineStart());
        assertEquals("getLineEnd()", Integer.MAX_VALUE, phi.getLineEnd());

        lhi = linemap.get(lineno + 1);
        assertNotNull("get LineHighlight", lhi);
        assertEquals("getLelide()", 0, lhi.getLelide());
        assertEquals("getRelide()", 0, lhi.getRelide());
        assertEquals("countMarkups()", 1, lhi.countMarkups());

        phi = lhi.getMarkup(0);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("getLineStart()", -1, phi.getLineStart());
        assertEquals("getLineEnd()", 6, phi.getLineEnd());
    }

    @Test
    public void testLinesSpanningMatch() {
        Passage p = new Passage();
        p.setStartOffset(0);
        p.setEndOffset(DOC2.length());
        p.addMatch(0, p.getEndOffset(), new BytesRef(DOC2), 1);
        assertEquals("getNumMatches()", 1, p.getNumMatches());

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter2);

        assertEquals("linemap size()", 3, linemap.size());
        int lineno = linemap.firstKey();
        assertEquals("first lineno", 0, lineno);
        assertTrue("linemap[1] exists", linemap.containsKey(1));
        assertTrue("linemap[2] exists", linemap.containsKey(2));

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull("get LineHighlight", lhi);
        assertEquals("getLelide()", 0, lhi.getLelide());
        assertEquals("getRelide()", 0, lhi.getRelide());
        assertEquals("countMarkups()", 1, lhi.countMarkups());

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("getLineStart()", 0, phi.getLineStart());
        assertEquals("getLineEnd()", Integer.MAX_VALUE, phi.getLineEnd());

        lhi = linemap.get(lineno + 1);
        assertNotNull("get LineHighlight", lhi);
        assertEquals("getLelide()", 0, lhi.getLelide());
        assertEquals("getRelide()", 0, lhi.getRelide());
        assertEquals("countMarkups()", 1, lhi.countMarkups());

        phi = lhi.getMarkup(0);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("getLineStart()", -1, phi.getLineStart());
        assertEquals("getLineEnd()", Integer.MAX_VALUE, phi.getLineEnd());

        lhi = linemap.get(lineno + 2);
        assertNotNull("get LineHighlight", lhi);
        assertEquals("getLelide()", 0, lhi.getLelide());
        assertEquals("getRelide()", 0, lhi.getRelide());
        assertEquals("countMarkups()", 1, lhi.countMarkups());

        phi = lhi.getMarkup(0);
        assertNotNull("get PhraseHighlight", phi);
        assertEquals("getLineStart()", -1, phi.getLineStart());
        assertEquals("getLineEnd()", 3, phi.getLineEnd());
    }

    private static PassageConverter getConverter(short contextCount) {
        ContextArgs args = new ContextArgs(contextCount, (short) 10);
        return new PassageConverter(args);
    }
}
