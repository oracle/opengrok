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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import static org.opengrok.indexer.util.StreamUtils.copyStream;

/**
 * Represents a container for custom test assertion methods.
 */
public class CustomAssertions {
    /**
     * non-public so as to be just a static container class.
     */
    protected CustomAssertions() {
    }

    /**
     * Asserts the specified strings have equal contents, comparing line-wise
     * after splitting on LFs.
     * @param messagePrefix a message prefixed to line-specific or length-
     * specific errors
     * @param expected the expected content
     * @param actual the actual content
     */
    public static void assertLinesEqual(String messagePrefix, String expected, String actual) {
        String[] expecteds = expected.split("\n");
        String[] gotten = actual.split("\n");
        assertLinesEqual(messagePrefix, expecteds, gotten);
    }

    /**
     * Asserts the specified lines arrays have equal contents.
     * @param messagePrefix a message prefixed to line-specific or length-
     * specific errors
     * @param expecteds the expected content of lines
     * @param actuals the actual content of lines
     */
    public static void assertLinesEqual(String messagePrefix, String[] expecteds, String[] actuals) {

        List<Integer> diffLines = new ArrayList<>();

        final int SHOW_N_DIFFS = 50;
        int ndiffs = 0;
        int lastDiff = -2;
        for (int i = 0; i < expecteds.length || i < actuals.length; i++) {
            if (i >= expecteds.length || i >= actuals.length ||
                !expecteds[i].equals(actuals[i])) {

                if (lastDiff + 1 != i && !diffLines.isEmpty()) {
                    printDiffs(expecteds, actuals, diffLines);
                    diffLines.clear();
                }
                ++ndiffs;
                lastDiff = i;
                diffLines.add(i);
                if (ndiffs >= SHOW_N_DIFFS) {
                    break;
                }
            }
        }
        if (!diffLines.isEmpty()) {
            printDiffs(expecteds, actuals, diffLines);
            diffLines.clear();
        }

        assertEquals(messagePrefix + "--should have no diffs", 0, ndiffs);
        assertEquals(messagePrefix + "--number of lines", expecteds.length, actuals.length);
    }

    /**
     * Asserts the specified tokenizer class produces an expected stream of
     * symbols from the specified input.
     * @param klass the test class
     * @param iss the input stream
     * @param expectedTokens the expected, ordered token list
     * @throws java.lang.Exception if an error occurs constructing a
     * {@code klass} instance or testing the stream
     */
    public static void assertSymbolStream(Class<? extends JFlexSymbolMatcher> klass, InputStream iss,
            List<String> expectedTokens) throws Exception {

        byte[] inputCopy = copyStream(iss);
        String input = new String(inputCopy, StandardCharsets.UTF_8);
        JFlexTokenizer tokenizer = new JFlexTokenizer(
            klass.getConstructor(Reader.class).newInstance(
                    new InputStreamReader(new ByteArrayInputStream(inputCopy), StandardCharsets.UTF_8)));

        CharTermAttribute term = tokenizer.addAttribute(
            CharTermAttribute.class);
        OffsetAttribute offs = tokenizer.addAttribute(OffsetAttribute.class);

        int count = 0;
        List<String> tokens = new ArrayList<>();
        while (tokenizer.incrementToken()) {
            String termValue = term.toString();
            tokens.add(termValue);

            String cutValue = input.substring(offs.startOffset(),
                offs.endOffset());
            assertEquals("cut term" + (1 + count), cutValue, termValue);
            ++count;
        }

        count = 0;
        for (String token : tokens) {
            // 1-based offset to accord with line #
            if (count >= expectedTokens.size()) {
                printTokens(tokens);
                assertTrue("too many tokens at term" + (1 + count) + ": " +
                    token, count < expectedTokens.size());
            }
            String expected = expectedTokens.get(count);
            if (!token.equals(expected)) {
                printTokens(tokens);
                assertEquals("term" + (1 + count), expected, token);
            }
            count++;
        }

        if (expectedTokens.size() != count) {
            printTokens(tokens);
            assertEquals("wrong number of tokens", expectedTokens.size(), count);
        }
    }

    private static void printDiffs(String[] expecteds, String[] actuals, List<Integer> diffLines) {
        if (diffLines.size() < 1) {
            return;
        }

        int ln0 = diffLines.get(0);
        int numln = diffLines.size();
        int loff = (Math.min(ln0, expecteds.length)) + 1;
        int lnum = count_within(expecteds.length, ln0, numln);
        int roff = (Math.min(ln0, actuals.length)) + 1;
        int rnum = count_within(actuals.length, ln0, numln);

        System.out.format("@@ -%d,%d +%d,%d @@", loff, lnum, roff, rnum);
        System.out.println();

        for (int i : diffLines) {
            if (i >= expecteds.length) {
                break;
            } else {
                System.out.print("- ");
                System.out.println(expecteds[i]);
            }
        }
        for (int i : diffLines) {
            if (i >= actuals.length) {
                break;
            } else {
                System.out.print("+ ");
                System.out.println(actuals[i]);
            }
        }
    }

    private static int count_within(int maxoffset, int ln0, int numln) {
        while (numln > 0) {
            if (ln0 + numln <= maxoffset) {
                return numln;
            }
            --numln;
        }
        return 0;
    }

    /**
     * Outputs a token list to stdout for use in a competent diffing tool
     * to compare to e.g. samplesymbols.txt.
     */
    private static void printTokens(List<String> tokens) {
        System.out.println("BEGIN TOKENS\n=====");
        for (String token : tokens) {
            System.out.println(token);
        }
        System.out.println("=====\nEND TOKENS");
    }
}
