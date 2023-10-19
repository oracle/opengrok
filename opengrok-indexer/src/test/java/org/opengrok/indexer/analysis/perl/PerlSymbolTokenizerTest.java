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
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.perl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Unit tests for {@link PerlSymbolTokenizer}.
 */
class PerlSymbolTokenizerTest {

    /**
     * Helper method for {@link #testOffsetAttribute()} that runs the test on
     * one single implementation class with the specified input text and
     * expected tokens.
     */
    private void testOffsetAttribute(Class<? extends JFlexSymbolMatcher> klass,
            String inputText, String[] expectedTokens)
            throws Exception {
        JFlexSymbolMatcher matcher = klass.getConstructor(Reader.class).
                newInstance(new StringReader(inputText));
        JFlexTokenizer tokenizer = new JFlexTokenizer(matcher);

        CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
        OffsetAttribute offset = tokenizer.addAttribute(OffsetAttribute.class);

        int count = 0;
        while (tokenizer.incrementToken()) {
            assertTrue(count < expectedTokens.length, "too many tokens");
            String expected = expectedTokens[count];
            // 0-based offset to accord with String[]
            assertEquals(expected, term.toString(), "term" + count);
            assertEquals(inputText.indexOf(expected), offset.startOffset(), "start" + count);
            assertEquals(inputText.indexOf(expected) + expected.length(),
                    offset.endOffset(), "end" + count);
            count++;
        }

        assertEquals(expectedTokens.length, count, "wrong number of tokens");
    }

    @Test
    void testPerlVariableInBraces() throws Exception {
        // Perl command to tokenize
        String inputText = "$ {abc} = 1; '$gh'; \"$ { VARIABLE  } $def xyz\";";
        String[] expectedTokens = {"abc", "VARIABLE", "def"};
        testOffsetAttribute(PerlSymbolTokenizer.class, inputText, expectedTokens);
    }

    @Test
    void testPerlWordCharDelimiters() throws Exception {
        // Perl command to tokenize
        String inputText = "qr z$abcz; qr z$defziz; qr i$ghixi;";

        String[] expectedTokens = {"abc", "def", "gh"};
        testOffsetAttribute(PerlSymbolTokenizer.class, inputText, expectedTokens);
    }

    /**
     * Test sample.pl v. samplesymbols.txt
     * @throws Exception exception
     */
    @Test
    void testPerlSymbolStream() throws Exception {
        InputStream plres = getClass().getClassLoader().getResourceAsStream(
            "analysis/perl/sample.pl");
        InputStream wdsres = getClass().getClassLoader().getResourceAsStream(
            "analysis/perl/samplesymbols.txt");

        List<String> expectedSymbols = readSampleSymbols(wdsres);
        assertSymbolStream(PerlSymbolTokenizer.class, plres, expectedSymbols);
    }
}
