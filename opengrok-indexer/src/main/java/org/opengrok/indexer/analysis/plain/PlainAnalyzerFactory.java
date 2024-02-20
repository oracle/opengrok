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
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2021, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;
import org.opengrok.indexer.util.IOUtils;

/**
 * Represents a subclass of {@link FileAnalyzerFactory} for plain-text
 * files in ASCII, UTF-8, or UTF-16.
 */
public final class PlainAnalyzerFactory extends FileAnalyzerFactory {

    private static final String NAME = "Plain Text";

    private static final int MIN_CHARS_WHILE_REMAINING = 20;

    // Up to 4 octets per UTF-8 character
    private static final int TRY_UTF8_BYTES = MIN_CHARS_WHILE_REMAINING * 4;

    /**
     * The reentrant {@link Matcher} implementation for plain-text files.
     */
    public static final Matcher MATCHER = new Matcher() {
            @Override
            public String description() {
                return "UTF-8, UTF-16BE, or UTF-16LE Byte Order Mark is present; or initial " +
                        "bytes are all UTF-8-encoded graphic characters or whitespace";
            }

            @Override
            public AnalyzerFactory isMagic(byte[] content, InputStream in) throws IOException {
                int lengthBOM = IOUtils.skipForBOM(content);
                if (lengthBOM > 0) {
                    return DEFAULT_INSTANCE;
                }
                if (readSomePlainCharactersUTF8noBOMwithoutError(in)) {
                    return DEFAULT_INSTANCE;
                }
                return null;
            }

            @Override
            public AnalyzerFactory forFactory() {
                return DEFAULT_INSTANCE;
            }
    };

    /**
     * Gets the singleton, factory instance that associates
     * {@link PlainAnalyzer} with files whose initial bytes are the UTF-8,
     * UTF-16BE, or UTF-16LE Byte Order Mark; or whose initial bytes are all
     * UTF-8-encoded graphic characters or whitespace.
     */
    public static final PlainAnalyzerFactory DEFAULT_INSTANCE = new PlainAnalyzerFactory();

    private PlainAnalyzerFactory() {
        super(null, null, null, null, MATCHER, "text/plain", AbstractAnalyzer.Genre.PLAIN, NAME, true);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new PlainAnalyzer(this);
    }

    private static boolean readSomePlainCharactersUTF8noBOMwithoutError(InputStream in)
            throws IOException {

        boolean isEOF = false;
        byte[] bytes = new byte[TRY_UTF8_BYTES];
        in.mark(TRY_UTF8_BYTES);
        int len = in.read(bytes);
        in.reset();
        if (len < 1) {
            return false;
        }
        if (len != TRY_UTF8_BYTES) {
            bytes = Arrays.copyOf(bytes, len);
            isEOF = true;
        }

        /*
         * Decode one character at a time until either a decoding error occurs
         * (failure) or the minimum number of required, valid characters is
         * reached (success).
         *
         * "Decode bytes to chars one at a time"
         * answered by https://stackoverflow.com/users/1831293/evgeniy-dorofeev
         * https://stackoverflow.com/questions/17227331/decode-bytes-to-chars-one-at-a-time
         * asked by https://stackoverflow.com/users/244360/kong
         *
         * Used under CC 4 with modifications noted as follows as required by
         * license:
         * * 2021-08-15 -- cfraire@me.com, revised to check for errors.
         */
        CharsetDecoder cd = StandardCharsets.UTF_8.newDecoder().
                onMalformedInput(CodingErrorAction.REPORT).
                onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer bin = ByteBuffer.wrap(bytes);
        CharBuffer out = CharBuffer.allocate(MIN_CHARS_WHILE_REMAINING);
        int numCharacters = 0;
        CoderResult decodeResult = cd.decode(bin, out, isEOF);
        if (decodeResult.isError()) {
            return false;
        }

        int numChars = out.position();
        out.position(0);
        for (int i = 0; i < numChars; ++i) {
            char c = out.charAt(i);
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                return false;
            }
            if (++numCharacters >= MIN_CHARS_WHILE_REMAINING) {
                return true;
            }
        }
        /*
         * At this point, as no error has occurred, then if any character was
         * read, consider the input as plain text.
         */
        return (numCharacters > 0);
    }
}
