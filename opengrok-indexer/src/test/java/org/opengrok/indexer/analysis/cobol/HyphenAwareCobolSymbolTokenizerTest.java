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
 * Copyright (c) 2026, Siyabend Urun <urunsiyabend@gmail.com>.
 */
package org.opengrok.indexer.analysis.cobol;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.JFlexTokenizer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link HyphenAwareCobolFixedSymbolTokenizer} and
 * {@link HyphenAwareCobolFreeSymbolTokenizer} emit each hyphen-separated piece
 * of a COBOL identifier as an additional token alongside the full identifier.
 *
 * <p>This is the workaround for OpenGrok's query-time {@code PlainSymbolTokenizer}
 * splitting on {@code -}: without per-piece tokens in the index, queries like
 * {@code WS-CARD-NUM} would never match the single-token indexed form.
 */
class HyphenAwareCobolSymbolTokenizerTest {

    @Test
    void fixedFormatEmitsFullIdentifierAndPieces() throws Exception {
        // Two hyphenated identifiers, one plain identifier (X) for control.
        String src = "000100     MOVE WS-CARD-NUM TO WS-NAME.\n"
                   + "000200     DISPLAY X.\n";

        List<String> tokens = collect(new HyphenAwareCobolFixedSymbolTokenizer(
                new StringReader(src)));

        // MOVE/TO/DISPLAY are keywords -> filtered out.
        // Each hyphenated identifier emits: full, then each non-empty piece.
        assertEquals(List.of(
                "WS-CARD-NUM", "WS", "CARD", "NUM",
                "WS-NAME", "WS", "NAME",
                "X"
        ), tokens);
    }

    @Test
    void freeFormatEmitsFullIdentifierAndPieces() throws Exception {
        String src = "    move ws-card-num to ws-name.\n";

        List<String> tokens = collect(new HyphenAwareCobolFreeSymbolTokenizer(
                new StringReader(src)));

        assertEquals(List.of(
                "ws-card-num", "ws", "card", "num",
                "ws-name", "ws", "name"
        ), tokens);
    }

    @Test
    void plainIdentifiersAreUnchanged() throws Exception {
        // No hyphens -> no extra tokens emitted.
        String src = "000100     DISPLAY MYVAR.\n";

        List<String> tokens = collect(new HyphenAwareCobolFixedSymbolTokenizer(
                new StringReader(src)));

        assertEquals(List.of("MYVAR"), tokens);
    }

    @Test
    void pieceOffsetsPointBackToTheSourceSlice() throws Exception {
        String src = "000100     MOVE WS-CARD-NUM TO X.\n";
        // 'WS-CARD-NUM' starts at column 16 (0-indexed).
        int identStart = src.indexOf("WS-CARD-NUM");

        HyphenAwareCobolFixedSymbolTokenizer lexer =
                new HyphenAwareCobolFixedSymbolTokenizer(new StringReader(src));
        JFlexTokenizer tk = new JFlexTokenizer((JFlexSymbolMatcher) lexer);
        CharTermAttribute term = tk.addAttribute(CharTermAttribute.class);
        OffsetAttribute offs = tk.addAttribute(OffsetAttribute.class);

        // Expected offsets relative to the source string.
        int[][] expected = {
                {identStart, identStart + 11}, // WS-CARD-NUM
                {identStart, identStart + 2},  // WS
                {identStart + 3, identStart + 7}, // CARD
                {identStart + 8, identStart + 11} // NUM
        };
        for (int[] exp : expected) {
            assertEquals(true, tk.incrementToken(), "token expected");
            assertEquals(exp[0], offs.startOffset(), "start of " + term);
            assertEquals(exp[1], offs.endOffset(), "end of " + term);
        }
    }

    private static List<String> collect(JFlexSymbolMatcher matcher) throws Exception {
        JFlexTokenizer tk = new JFlexTokenizer(matcher);
        CharTermAttribute term = tk.addAttribute(CharTermAttribute.class);
        List<String> out = new ArrayList<>();
        while (tk.incrementToken()) {
            out.add(term.toString());
        }
        return out;
    }
}