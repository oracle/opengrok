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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.util.SourceSplitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Represents a container for tests of {@link PassageConverter} etc.
 */
class PassageConverterTest {

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

    @BeforeAll
    static void setUpClass() throws Exception {
        splitter = new SourceSplitter();
        splitter.reset(DOC);
        splitter2 = new SourceSplitter();
        splitter2.reset(DOC2);
    }

    @Test
    void testOneWord() {
        final String WORD = "gravida";
        int woff = DOC.indexOf(WORD);
        assertTrue(woff >= 0, WORD);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter);

        assertEquals(1, linemap.size(), "linemap size()");
        int lineno = linemap.firstKey();
        assertEquals(2, lineno, "lineno");

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull(lhi, "get LineHighlight");
        assertEquals(0, lhi.getLelide(), "getLelide()");
        assertEquals(0, lhi.getRelide(), "getRelide()");
        assertEquals(1, lhi.countMarkups(), "countMarkups()");

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(32, phi.getLineStart(), "getLineStart()");
        assertEquals(32 + WORD.length(), phi.getLineEnd(), "getLineEnd()");
    }

    @Test
    void testOneWordElided() {
        final String WORD = "dignissim";
        int woff = DOC.indexOf(WORD);
        assertTrue(woff >= 0, WORD);

        Passage p = new Passage();
        p.setStartOffset(woff);
        p.setEndOffset(woff + WORD.length());
        p.addMatch(woff, p.getEndOffset(), new BytesRef(WORD), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter);

        assertEquals(1, linemap.size(), "linemap size()");
        int lineno = linemap.firstKey();
        assertEquals(5, lineno, "lineno");

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull(lhi, "get LineHighlight");
        assertEquals(41, lhi.getLelide(), "getLelide()");
        assertEquals(139, lhi.getRelide(), "getRelide()");
        assertEquals(139 - 41, cvt.getArgs().getContextWidth() - 2, "context width minus 2");
        assertEquals(1, lhi.countMarkups(), "countMarkups()");

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(113, phi.getLineStart(), "getLineStart()");
        assertEquals(113 + WORD.length(), phi.getLineEnd(), "getLineEnd()");
    }

    @Test
    void testTwoWordsElided() {
        final String WORD1 = "Maecenas";
        int woff1 = DOC.indexOf(WORD1);
        assertTrue(woff1 >= 0, WORD1);

        final String WORD2 = "rhoncus";
        int woff2 = DOC.indexOf(WORD2);
        assertTrue(woff2 >= 0, WORD2);

        Passage p = new Passage();
        p.setStartOffset(woff1);
        p.setEndOffset(woff2 + WORD2.length());
        p.addMatch(woff1, woff1 + WORD1.length(), new BytesRef(WORD1), 1);
        p.addMatch(woff2, woff2 + WORD2.length(), new BytesRef(WORD2), 1);
        assertEquals(2, p.getNumMatches(), "getNumMatches()");

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter);

        assertEquals(1, linemap.size(), "linemap size()");
        int lineno = linemap.firstKey();
        assertEquals(5, lineno, "lineno");

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull(lhi, "get LineHighlight");
        assertEquals(4, lhi.getLelide(), "getLelide()");
        assertEquals(102, lhi.getRelide(), "getRelide()");
        assertEquals(139 - 41, cvt.getArgs().getContextWidth() - 2, "context width minus 2");
        assertEquals(2, lhi.countMarkups(), "countMarkups()");

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(4, phi.getLineStart(), "0:getLineStart()");
        assertEquals(4 + WORD1.length(), phi.getLineEnd(), "0:getLineEnd()");

        phi = lhi.getMarkup(1);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(132, phi.getLineStart(), "1:getLineStart()");
        assertEquals(132 + WORD2.length(), phi.getLineEnd(), "1:getLineEnd()");
    }

    @Test
    void testLineSpanningMatch() {
        final String PHRASE = "elit.\nMauris";
        int poff = DOC.indexOf(PHRASE);
        assertTrue(poff >= 0, PHRASE);

        Passage p = new Passage();
        p.setStartOffset(poff);
        p.setEndOffset(poff + PHRASE.length());
        p.addMatch(poff, p.getEndOffset(), new BytesRef(PHRASE), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter);

        assertEquals(2, linemap.size(), "linemap size()");
        int lineno = linemap.firstKey();
        assertEquals(0, lineno, "first lineno");
        assertTrue(linemap.containsKey(1), "linemap[1] exists");

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull(lhi, "get LineHighlight");
        assertEquals(0, lhi.getLelide(), "getLelide()");
        assertEquals(0, lhi.getRelide(), "getRelide()");
        assertEquals(1, lhi.countMarkups(), "countMarkups()");

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(55, phi.getLineStart(), "getLineStart()");
        assertEquals(Integer.MAX_VALUE, phi.getLineEnd(), "getLineEnd()");

        lhi = linemap.get(lineno + 1);
        assertNotNull(lhi, "get LineHighlight");
        assertEquals(0, lhi.getLelide(), "getLelide()");
        assertEquals(0, lhi.getRelide(), "getRelide()");
        assertEquals(1, lhi.countMarkups(), "countMarkups()");

        phi = lhi.getMarkup(0);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(-1, phi.getLineStart(), "getLineStart()");
        assertEquals(6, phi.getLineEnd(), "getLineEnd()");
    }

    @Test
    void testLinesSpanningMatch() {
        Passage p = new Passage();
        p.setStartOffset(0);
        p.setEndOffset(DOC2.length());
        p.addMatch(0, p.getEndOffset(), new BytesRef(DOC2), 1);
        assertEquals(1, p.getNumMatches(), "getNumMatches()");

        PassageConverter cvt = getConverter((short) 0);
        SortedMap<Integer, LineHighlight> linemap =
                cvt.convert(new Passage[] {p}, splitter2);

        assertEquals(3, linemap.size(), "linemap size()");
        int lineno = linemap.firstKey();
        assertEquals(0, lineno, "first lineno");
        assertTrue(linemap.containsKey(1), "linemap[1] exists");
        assertTrue(linemap.containsKey(2), "linemap[2] exists");

        LineHighlight lhi = linemap.get(lineno);
        assertNotNull(lhi, "get LineHighlight");
        assertEquals(0, lhi.getLelide(), "getLelide()");
        assertEquals(0, lhi.getRelide(), "getRelide()");
        assertEquals(1, lhi.countMarkups(), "countMarkups()");

        PhraseHighlight phi = lhi.getMarkup(0);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(0, phi.getLineStart(), "getLineStart()");
        assertEquals(Integer.MAX_VALUE, phi.getLineEnd(), "getLineEnd()");

        lhi = linemap.get(lineno + 1);
        assertNotNull(lhi, "get LineHighlight");
        assertEquals(0, lhi.getLelide(), "getLelide()");
        assertEquals(0, lhi.getRelide(), "getRelide()");
        assertEquals(1, lhi.countMarkups(), "countMarkups()");

        phi = lhi.getMarkup(0);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(-1, phi.getLineStart(), "getLineStart()");
        assertEquals(Integer.MAX_VALUE, phi.getLineEnd(), "getLineEnd()");

        lhi = linemap.get(lineno + 2);
        assertNotNull(lhi, "get LineHighlight");
        assertEquals(0, lhi.getLelide(), "getLelide()");
        assertEquals(0, lhi.getRelide(), "getRelide()");
        assertEquals(1, lhi.countMarkups(), "countMarkups()");

        phi = lhi.getMarkup(0);
        assertNotNull(phi, "get PhraseHighlight");
        assertEquals(-1, phi.getLineStart(), "getLineStart()");
        assertEquals(3, phi.getLineEnd(), "getLineEnd()");
    }

    private static PassageConverter getConverter(short contextCount) {
        ContextArgs args = new ContextArgs(contextCount, (short) 10);
        return new PassageConverter(args);
    }
}
