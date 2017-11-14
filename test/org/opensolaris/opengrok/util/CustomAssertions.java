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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;

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
     * Asserts the specified lines arrays have equal contents.
     * @param messagePrefix a message prefixed to line-specific or length-
     * specific errors
     * @param expecteds the expected content of lines
     * @param actuals the actual content of lines
     */
    public static void assertLinesEqual(String messagePrefix,
        String expecteds[], String actuals[]) {

        for (int i = 0; i < expecteds.length && i < actuals.length; i++) {
            if (!expecteds[i].equals(actuals[i])) {
                System.out.print("- ");
                System.out.println(expecteds[i]);
                System.out.print("+ ");
                System.out.println(actuals[i]);
            }
            assertEquals(messagePrefix + ":line " + (i + 1), expecteds[i],
                actuals[i]);
        }

        assertEquals(messagePrefix + ":number of lines", expecteds.length,
            actuals.length);
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
    public static void assertSymbolStream(Class<? extends JFlexTokenizer> klass,
        InputStream iss, List<String> expectedTokens) throws Exception {

        JFlexTokenizer tokenizer = klass.getConstructor(Reader.class).
            newInstance(new InputStreamReader(iss, "UTF-8"));

        CharTermAttribute term = tokenizer.addAttribute(
            CharTermAttribute.class);

        int count = 0;
        while (tokenizer.incrementToken()) {
            assertTrue("too many tokens at term" + (1 + count) + ": " +
                term.toString(), count < expectedTokens.size());
            String expected = expectedTokens.get(count);
            // 1-based offset to accord with line #
            assertEquals("term" + (1 + count), expected, term.toString());
            count++;
        }

        assertEquals("wrong number of tokens", expectedTokens.size(), count);
    }
}
