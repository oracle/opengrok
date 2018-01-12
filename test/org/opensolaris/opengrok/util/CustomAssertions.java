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
 * Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import org.opensolaris.opengrok.analysis.TokenizerMode;
import static org.opensolaris.opengrok.util.StreamUtils.copyStream;

/**
 * Represents a container for custom test assertion methods
 */
public class CustomAssertions {
    /**
     * non-public so as to be just a static container class
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
    public static void assertLinesEqual(String messagePrefix, String expected,
        String actual) {

        String expecteds[] = expected.split("\n");
        String gotten[] = actual.split("\n");
        assertLinesEqual(messagePrefix, expecteds, gotten);
    }

    /**
     * Asserts the specified lines arrays have equal contents.
     * @param messagePrefix a message prefixed to line-specific or length-
     * specific errors
     * @param expecteds the expected content of lines
     * @param actuals the actual content of lines
     */
    public static void assertLinesEqual(String messagePrefix,
        String expecteds[], String actuals[]) {

        List<Integer> diffLines = new ArrayList<>();

        final int SHOW_N_DIFFS = 10;
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
                if (ndiffs >= SHOW_N_DIFFS) break;
            }
        }
        if (!diffLines.isEmpty()) {
            printDiffs(expecteds, actuals, diffLines);
            diffLines.clear();
        }

        assertTrue(messagePrefix + "--should have no diffs", ndiffs == 0);
        assertEquals(messagePrefix + "--number of lines", expecteds.length,
            actuals.length);
    }

    /**
     * Calls
     * {@link #assertSymbolStream(java.lang.Class, java.io.InputStream, java.util.List, boolean)}
     * with {@code klass}, {@code iss}, {@code expectedTokens} and
     * {@code false}, and {@code TokenizerMode.SYMBOLS_ONLY}.
     * @param klass the test class
     * @param iss the input stream
     * @param expectedTokens the expected, ordered token list
     * @throws java.lang.Exception if an error occurs constructing a
     * {@code klass} instance or testing the stream
     */
    public static void assertSymbolStream(
        Class<? extends JFlexSymbolMatcher> klass, InputStream iss,
        List<String> expectedTokens) throws Exception {
        assertSymbolStream(klass, iss, expectedTokens, false,
            TokenizerMode.SYMBOLS_ONLY);
    }

    /**
     * Calls
     * {@link #assertSymbolStream2(java.lang.Class, java.io.InputStream, java.util.List, boolean, org.opensolaris.opengrok.analysis.TokenizerMode)}
     * with {@code klass}, {@code iss}, a translation of {@code expectedTokens}
     * with {@code null} values, {@code caseInsensitive}, and {@code mode}.
     * @param klass the test class
     * @param iss the input stream
     * @param expectedTokens the expected, ordered token list
     * @param caseInsensitive indicates if content should be checked against
     * source in case-insensitive (i.e. lower-cased) manner
     * @param mode indicates mode for
     * {@link JFlexTokenizer#setTokenizerMode(org.opensolaris.opengrok.analysis.TokenizerMode)}
     * @throws java.lang.Exception if an error occurs constructing a
     * {@code klass} instance or testing the stream
     */
    public static void assertSymbolStream(
        Class<? extends JFlexSymbolMatcher> klass, InputStream iss,
        List<String> expectedTokens, boolean caseInsensitive,
        TokenizerMode mode) throws Exception {

        List<SimpleEntry<String, Integer>> kvs =
            expectedTokens.stream().map((s) ->
            new SimpleEntry<>(s, (Integer)null)).collect(
            Collectors.toList());
        assertSymbolStream2(klass, iss, kvs, caseInsensitive, mode);
    }

    /**
     * Asserts the specified tokenizer class produces an expected stream of
     * symbols from the specified input.
     * @param klass the test class
     * @param iss the input stream
     * @param expectedTokens the expected, ordered token list, where the Integer
     * value is optional but asserted to match if not null
     * @param caseInsensitive indicates if content should be checked against
     * source in case-insensitive (i.e. lower-cased) manner
     * @param mode indicates mode for
     * {@link JFlexTokenizer#setTokenizerMode(org.opensolaris.opengrok.analysis.TokenizerMode)}
     * @throws java.lang.Exception if an error occurs constructing a
     * {@code klass} instance or testing the stream
     */
    public static void assertSymbolStream2(
        Class<? extends JFlexSymbolMatcher> klass, InputStream iss,
        List<SimpleEntry<String, Integer>> expectedTokens,
        boolean caseInsensitive, TokenizerMode mode) throws Exception {

        byte[] inputCopy = copyStream(iss);
        String input = new String(inputCopy, StandardCharsets.UTF_8);
        JFlexTokenizer tokenizer = new JFlexTokenizer(
            klass.getConstructor(Reader.class).newInstance(
	        new InputStreamReader(new ByteArrayInputStream(inputCopy),
	        StandardCharsets.UTF_8)));
        tokenizer.setTokenizerMode(mode);

        CharTermAttribute term = tokenizer.getAttribute(
            CharTermAttribute.class);
        OffsetAttribute offs = tokenizer.getAttribute(OffsetAttribute.class);
        PositionIncrementAttribute pinc = tokenizer.getAttribute(
            PositionIncrementAttribute.class);

        int count = 0;
        List<SimpleEntry<String, Integer>> tokens = new ArrayList<>();
        while (tokenizer.incrementToken()) {
            String termValue = term.toString();
            Integer v = pinc != null ? pinc.getPositionIncrement() : null;
            tokens.add(new SimpleEntry<>(termValue, v));

            String cutValue = input.substring(offs.startOffset(),
                offs.endOffset());
            if (caseInsensitive) {
                cutValue = cutValue.toLowerCase();
            }
            assertEquals("cut term" + (1 + count), cutValue, termValue);
            ++count;
        }

        boolean anyPosIncs = anyPositionIncrements(expectedTokens);

        count = 0;
        for (SimpleEntry<String, Integer> token : tokens) {
            // 1-based offset to accord with line #
            if (count >= expectedTokens.size()) {
                printTokens(tokens, anyPosIncs);
                assertTrue("too many tokens at term" + (1 + count) + ": " +
                    token, count < expectedTokens.size());
            }
            SimpleEntry<String, Integer> expected = expectedTokens.get(count);
            if (!token.getKey().equals(expected.getKey())) {
                printTokens(tokens, anyPosIncs);
                assertEquals("term" + (1 + count), expected.getKey(),
                    token.getKey());
            }
            Integer expv = expected.getValue();
            if (expv != null && !expv.equals(token.getValue())) {
                printTokens(tokens, anyPosIncs);
                assertEquals("posinc" + (1 + count), expv, token.getValue());
            }

            count++;
        }

        assertEquals("wrong number of tokens", expectedTokens.size(), count);
    }

    private static void printDiffs(String expecteds[], String actuals[],
        List<Integer> diffLines) {

        if (diffLines.size() < 1) return;

        int ln0 = diffLines.get(0);
        int numln = diffLines.size();
        int loff = (ln0 < expecteds.length ? ln0 : expecteds.length) + 1;
        int lnum = count_within(expecteds.length, ln0, numln);
        int roff = (ln0 < actuals.length ? ln0 : actuals.length) + 1;
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
            if (ln0 + numln <= maxoffset) return numln;
            --numln;
        }
        return 0;
    }

    /**
     * Outputs a token list to stdout for use in a competent diffing tool
     * to compare to e.g. samplesymbols.txt.
     */
    private static void printTokens(List<SimpleEntry<String, Integer>> tokens,
        boolean withPosIncrements) {

        System.out.println("BEGIN TOKENS =====");
        for (int i = 0; i < tokens.size(); ++i) {
            SimpleEntry<String, Integer> kv = tokens.get(i);
            System.out.print(kv.getKey());

            if (withPosIncrements) {
                Integer v = kv.getValue();
                if (v != null) {
                    System.out.print("\t|");
                    System.out.print(v);
                }
            }

            System.out.println();
        }
        System.out.println("===== END TOKENS");
    }

    private static boolean anyPositionIncrements(
        List<SimpleEntry<String, Integer>> expectedTokens) {

        for (SimpleEntry<String, Integer> etok : expectedTokens) {
            if (etok.getValue() != null) {
                return true;
            }
        }
        return false;
    }
}
