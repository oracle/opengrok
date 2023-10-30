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
 * Copyright (c) 2017, 2021, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzerFactory.Matcher;
import org.opengrok.indexer.util.IOUtils;

/**
 * Represents an implementation of {@link Matcher} that detects a troff- or mandoc-like document.
 */
public class DocumentMatcher implements Matcher {

    /**
     * Set to 512K {@code int}, but {@code NUMCHARS_FIRST_LOOK} and
     * {@code LINE_LIMIT} should apply beforehand. This value is "effectively
     * unbounded" without being literally 2_147_483_647 -- as the other limits
     * will apply first, and the {@link java.io.BufferedInputStream} will
     * manage a reasonably-sized buffer.
     */
    private static final int MARK_READ_LIMIT = 1024 * 512;

    private static final int LINE_LIMIT = 100;

    private static final int FIRST_LOOK_WIDTH = 300;

    private final AnalyzerFactory factory;

    private final String[] lineStarters;

    /**
     * Initializes an instance for the required parameters.
     * @param factory required factory to return when matched
     * @param lineStarters required list of line starters that indicate a match
     * @throws IllegalArgumentException thrown if any parameter is null
     */
    public DocumentMatcher(AnalyzerFactory factory, String[] lineStarters) {
        if (factory == null) {
            throw  new IllegalArgumentException("`factory' is null");
        }
        if (lineStarters == null) {
            throw  new IllegalArgumentException("`lineStarters' is null");
        }
        if (lineStarters.length < 1) {
            throw  new IllegalArgumentException("`lineStarters' is empty");
        }

        String[] copyOf = Arrays.copyOf(lineStarters, lineStarters.length);
        for (String elem : copyOf) {
            if (elem == null) {
                throw  new IllegalArgumentException(
                    "`lineStarters' has null element");
            }
        }

        this.factory = factory;
        this.lineStarters = copyOf;
    }

    /**
     * Try to match the file contents by looking for {@code lineStarters} in
     * the first 100 lines while also affirming that the document starts
     * with "." or "'" after a limited amount of whitespace.
     * <p>
     * The stream is reset before returning.
     *
     * @param contents the first few bytes of a file
     * @param in the input stream from which the full file can be read
     * @return an analyzer factory if the contents match, or {@code null}
     * otherwise
     * @throws IOException in case of any read error
     */
    @Override
    public AnalyzerFactory isMagic(byte[] contents, InputStream in)
        throws IOException {

        if (!in.markSupported()) {
            return null;
        }
        in.mark(MARK_READ_LIMIT);

        // read encoding, and skip past any BOM
        int bomLength = 0;
        String encoding = IOUtils.findBOMEncoding(contents);
        if (encoding == null) {
            encoding = StandardCharsets.UTF_8.name();
        } else {
            bomLength = IOUtils.skipForBOM(contents);
            if (in.skip(bomLength) != bomLength) {
                in.reset();
                return null;
            }
        }

        // affirm that a LF exists in a first block
        boolean foundLF = hasLineFeed(in, encoding);
        in.reset();
        if (!foundLF) {
            return null;
        }
        if (bomLength > 0 && in.skip(bomLength) != bomLength) {
            in.reset();
            return null;
        }

        // read line-by-line for a first few lines
        BufferedReader rdr = new BufferedReader(new InputStreamReader(
            in, encoding));
        boolean foundContent = false;
        int numFirstChars = 0;
        int numLines = 0;
        String line;
        while ((line = rdr.readLine()) != null) {
            for (String lineStarter : lineStarters) {
                if (line.startsWith(lineStarter)) {
                    in.reset();
                    return factory;
                }
            }
            if (++numLines >= LINE_LIMIT) {
                in.reset();
                return null;
            }

            // If not yet `foundContent', then only a limited allowance is
            // given until a sentinel '.' or '\'' must be seen after nothing
            // else but whitespace.
            if (!foundContent) {
                for (int i = 0; i < line.length() && numFirstChars <
                    FIRST_LOOK_WIDTH; ++i, ++numFirstChars) {
                    char c = line.charAt(i);
                    if (c == '.' || c == '\'') {
                        foundContent = true;
                        break;
                    } else if (!Character.isWhitespace(c)) {
                        in.reset();
                        return null;
                    }
                }
                if (!foundContent && numFirstChars >= FIRST_LOOK_WIDTH) {
                    in.reset();
                    return null;
                }
            }
        }

        in.reset();
        return null;
    }

    @Override
    public AnalyzerFactory forFactory() {
        return factory;
    }

    /**
     * Determines if the {@code in} stream has a line feed character within the
     * first {@code FIRST_LOOK_WIDTH} characters.
     * @param in the input stream has any BOM (not {@code reset} after use)
     * @param encoding the input stream charset
     * @return true if a line feed '\n' was found
     * @throws IOException thrown on any error in reading
     */
    private boolean hasLineFeed(InputStream in, String encoding)
            throws IOException {
        byte[] buf;
        int nextra;
        int noff;
        switch (encoding) {
            case "UTF-16LE":
                buf = new byte[FIRST_LOOK_WIDTH * 2];
                nextra = 1;
                noff = 0;
                break;
            case "UTF-16BE":
                buf = new byte[FIRST_LOOK_WIDTH * 2];
                nextra = 1;
                noff = 1;
                break;
            default:
                buf = new byte[FIRST_LOOK_WIDTH];
                nextra = 0;
                noff = 0;
                break;
        }

        int nread = in.read(buf);
        for (int i = 0; i + nextra < nread; i += 1 + nextra) {
            if (nextra > 0) {
                if (buf[i + noff] == '\n' && buf[i + 1 - noff] == '\0') {
                    return true;
                }
            } else {
                if (buf[i] == '\n') {
                    return true;
                }
            }
        }
        return false;
    }
}
