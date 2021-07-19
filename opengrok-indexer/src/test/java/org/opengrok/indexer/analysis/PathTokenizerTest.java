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
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis;

import java.io.StringReader;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test class for PathTokenizer.
 * @author Lubos Kosco
 */
public class PathTokenizerTest {

    /**
     * Test of incrementToken method, of class PathTokenizer.
     */
    @Test
    public void testIncrementToken() throws Exception {
        String inputText = "alpha/beta/gamma/delta.ext";
        String[] expectedTokens = inputText.split("[/.]");
        PathTokenizer tokenizer = new PathTokenizer();
        tokenizer.setReader(new StringReader(inputText));
        CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
        OffsetAttribute offset = tokenizer.addAttribute(OffsetAttribute.class);

        int count = 0;
        int dots = 0;
        tokenizer.reset();
        while (tokenizer.incrementToken()) {
            if (term.toString().equals(".")) {
                dots++;
                break;
            }
            assertTrue(count < expectedTokens.length, "too many tokens");
            String expected = expectedTokens[count];
            assertEquals(expected, term.toString(), "term");
            assertEquals(inputText.indexOf(expected), offset.startOffset(), "start");
            assertEquals(inputText.indexOf(expected) + expected.length(), offset.endOffset(), "end");
            count++;
        }
        tokenizer.end();
        tokenizer.close();
        assertEquals(expectedTokens.length, count + dots, "wrong number of tokens");
    }
}
