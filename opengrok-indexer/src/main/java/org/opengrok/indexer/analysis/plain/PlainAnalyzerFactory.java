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
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import java.io.IOException;
import java.io.InputStream;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;
import org.opengrok.indexer.util.IOUtils;

public final class PlainAnalyzerFactory extends FileAnalyzerFactory {

    private static final String name = "Plain Text";
    
    private static final Matcher MATCHER = new Matcher() {
            @Override
            public String description() {
                return "UTF-8, UTF-16BE, or UTF-16LE Byte Order Mark is" +
                    " present; or first eight bytes are all ASCII graphic" +
                    " characters or ASCII whitespace";
            }

            @Override
            public AnalyzerFactory isMagic(byte[] content, InputStream in)
                    throws IOException {
                if (isPlainText(content)) {
                    return DEFAULT_INSTANCE;
                } else {
                    return null;
                }
            }

            @Override
            public AnalyzerFactory forFactory() {
                return DEFAULT_INSTANCE;
            }

            /**
             * Check whether the byte array contains plain text. First, look
             * for a UTF BOM; otherwise, inspect as if US-ASCII.
             */
            private boolean isPlainText(byte[] content) throws IOException {
                int lengthBOM = IOUtils.skipForBOM(content);
                if (lengthBOM > 0) {
                    return true;
                }
                String ascii = new String(content, "US-ASCII");
                return isPlainText(ascii);
            }

            /**
             * Check whether the string only contains plain ASCII characters.
             */
            private boolean isPlainText(String str) {
                for (int i = 0; i < str.length(); i++) {
                    char b = str.charAt(i);
                    if ((b >= 32 && b < 127) || // ASCII printable characters
                            (b == 9)         || // horizontal tab
                            (b == 10)        || // line feed
                            (b == 12)        || // form feed
                            (b == 13)) {        // carriage return
                        // is plain text so far, go to next byte
                        continue;
                    } else {
                        // 8-bit values or unprintable control characters,
                        // probably not plain text
                        return false;
                    }
                }
                return true;
            }
        };

    public static final PlainAnalyzerFactory DEFAULT_INSTANCE =
            new PlainAnalyzerFactory();

    private PlainAnalyzerFactory() {
        super(null, null, null, null, MATCHER, "text/plain", AbstractAnalyzer.Genre.PLAIN, name);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new PlainAnalyzer(this);
    }
}
