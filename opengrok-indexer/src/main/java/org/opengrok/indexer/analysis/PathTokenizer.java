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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * Tokenizer for paths filenames and extensions Input:
 *
 * <pre>
 *  /topdir/subdir/filename.ext
 * </pre>
 *
 * Output:
 *
 * <pre>
 *  topdir
 *  subdir
 *  filename
 *  .
 *  ext
 * </pre>
 */
public class PathTokenizer extends Tokenizer {

    // below should be '/' since we try to convert even windows file separators
    // to unix ones
    public static final char DEFAULT_DELIMITER = '/';
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private int startPosition = 0;
    private final char delimiter;
    private int charsRead = 0;
    private boolean dot = false;
    private static final char cdot = '.';

    public PathTokenizer() {
        this.delimiter = DEFAULT_DELIMITER;
    }

    @Override
    public final boolean incrementToken() throws IOException {
        clearAttributes();
        if (dot) {
            dot = false;
            termAtt.setEmpty();
            termAtt.append(cdot);
            termAtt.setLength(1);
            offsetAtt.setOffset(correctOffset(startPosition), correctOffset(startPosition + 1));
            startPosition++;
            return true;
        }

        char[] buf = new char[64];
        int c;
        int i = 0;
        do {
            c = input.read();
            charsRead++;
            if (c == -1) {
                return false;
            }
        } while (c == delimiter);

        do {
            if (i >= buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            /*
              "In general, String.toLowerCase(Locale) should be used to map
              characters to lowercase. String case mapping methods have several
              benefits over Character case mapping methods. String case mapping
              methods can perform locale-sensitive mappings, context-sensitive
              mappings, and 1:M character mappings, whereas the Character case
              mapping methods cannot." See below.
             */
            buf[i++] = (char) c;
            c = input.read();
            charsRead++;
        } while (c != delimiter && c != cdot && !Character.isWhitespace(c) && c != -1);
        if (c == cdot) {
            dot = true;
        }
        String bufLcase = String.valueOf(buf, 0, i).toLowerCase(Locale.ROOT);
        i = bufLcase.length();
        termAtt.append(bufLcase);
        termAtt.setLength(i);
        offsetAtt.setOffset(correctOffset(startPosition), correctOffset(startPosition + i));
        startPosition = startPosition + i + 1;
        return true;
    }

    @Override
    public final void end() throws IOException {
        super.end();
        // set final offset
        int finalOffset = correctOffset(charsRead);
        offsetAtt.setOffset(finalOffset, finalOffset);
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        dot = false;
        charsRead = 0;
        startPosition = 0;
    }

}
