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
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opengrok.indexer.logger.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the behavior of the {@code HeadHandler} class. The main aspect to check is that the
 * input stream is read whole.
 */
@RunWith(Parameterized.class)
public class HeadHandlerTest {
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        List<Object[]> tests = new ArrayList<>();
        for (int i = 0; i < ThreadLocalRandom.current().nextInt(4, 8); i++) {
            tests.add(new Object[]{ThreadLocalRandom.current().nextInt(2, 10),
                    ThreadLocalRandom.current().nextInt(1, 10),
                    HeadHandler.getBufferedReaderSize() * ThreadLocalRandom.current().nextInt(1, 40)});
        }
        return tests;
    }

    private int lineCnt;
    private int headLineCnt;
    private int totalCharCount;

    private static final Logger LOGGER = LoggerFactory.getLogger(HeadHandlerTest.class);

    /**
     * @param lineCnt number of lines to generate
     * @param headLineCnt maximum number of lines to get
     */
    public HeadHandlerTest(int lineCnt, int headLineCnt, int totalCharCount) {
        this.lineCnt = lineCnt;
        this.headLineCnt = headLineCnt;
        this.totalCharCount = totalCharCount;
    }

    private static class RandomInputStream extends InputStream {
        private final int maxCharCount;
        private int charCount;
        private final int maxLines;
        private int lines;

        private final String letters;

        private static final String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private static final String numbers = "0123456789";

        private String result = "";

        int[] lineBreaks;

        /**
         * Generate alphanumeric characters.
         * @param maxCharCount number of characters to generate
         * @param maxLines number of lines to generate
         */
        RandomInputStream(int maxCharCount, int maxLines) {
            if (maxLines > maxCharCount) {
                throw new IllegalArgumentException("maxLines must be smaller than or equal to maxCharCount");
            }

            if (maxCharCount <= 0) {
                throw new IllegalArgumentException("maxCharCount must be positive number");
            }

            if (maxLines <= 1) {
                throw new IllegalArgumentException("maxLines must be strictly bigger than 1");
            }

            this.maxCharCount = maxCharCount;
            this.charCount = 0;
            this.maxLines = maxLines;
            this.lines = 0;

            letters = alpha + alpha.toLowerCase() + numbers;

            // Want the newlines generally to appear within the first half of the generated data
            // so that the handler has significant amount of data to read after it is done reading the lines.
            lineBreaks = new int[maxLines - 1];
            for (int i = 0; i < lineBreaks.length; i++) {
                lineBreaks[i] = ThreadLocalRandom.current().nextInt(1, maxCharCount / 2);
            }
        }

        int getCharCount() {
            return charCount;
        }

        String getResult() {
            return result;
        }

        @Override
        public int read() {
            int ret;
            if (charCount < maxCharCount) {
                if (charCount > 0 && lines < maxLines - 1 && charCount == lineBreaks[lines]) {
                    ret = '\n';
                    lines++;
                } else {
                    ret = letters.charAt(ThreadLocalRandom.current().nextInt(0, letters.length()));
                }
                result += String.format("%c", ret);
                charCount++;
                return ret;
            }

            return -1;
        }
    }

    @Test
    public void testHeadHandler() throws IOException {
        LOGGER.log(Level.INFO, "testing HeadHandler with: {0}/{1}/{2}",
                new Object[]{lineCnt, headLineCnt, totalCharCount});

        RandomInputStream rndStream = new RandomInputStream(totalCharCount, lineCnt);
        HeadHandler handler = new HeadHandler(headLineCnt);
        assertTrue(totalCharCount >= HeadHandler.getBufferedReaderSize(),
                "number of characters to generate must be bigger than " +
                        "HeadHandler internal buffer size");
        handler.processStream(rndStream);
        assertTrue(handler.count() <= headLineCnt,
                "HeadHandler should not get more lines than was asked to");
        assertEquals(totalCharCount, rndStream.getCharCount(),
                "HeadHandler should read all the characters from input stream");
        String[] headLines = new String[handler.count()];
        for (int i = 0; i < handler.count(); i++) {
            String line = handler.get(i);
            LOGGER.log(Level.INFO, "line [{0}]: {1}", new Object[]{i, line});
            headLines[i] = line;
        }
        assertArrayEquals(headLines,
                Arrays.copyOfRange(rndStream.getResult().split("\n"), 0, handler.count()),
                "the lines retrieved by HeadHandler needs to match the input");
    }
}
