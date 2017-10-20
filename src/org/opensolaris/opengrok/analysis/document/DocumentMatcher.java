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
package org.opensolaris.opengrok.analysis.document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory.Matcher;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Represents an implementation of {@link Matcher} that detects a troff-
 * or mandoc-like document
 */
public class DocumentMatcher implements Matcher {

    /**
     * Set to 512K {@code int}, but {@code NUMCHARS_FIRST_LOOK} and
     * {@code LINE_LIMIT} should apply beforehand
     */
    private static final int MARK_READ_LIMIT = 1024 * 512;

    private static final int LINE_LIMIT = 100;

    private static final int FIRST_LOOK_WIDTH = 300;

    private static final int FIRST_CONTENT_WIDTH = 8;

    private final FileAnalyzerFactory factory;

    private final String[] lineStarters;

    /**
     * Initializes an instance for the required parameters
     * @param factory required factory to return when matched
     * @param lineStarters required list of line starters that indicate a match
     * @throws IllegalArgumentException if any parameter is null
     */
    public DocumentMatcher(FileAnalyzerFactory factory, String[] lineStarters) {
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
     * Try to match the file contents by first affirming the document starts
     * with "." or "'" and then looks for {@code lineStarters} in the first
     * 100 lines.
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
    public FileAnalyzerFactory isMagic(byte[] contents, InputStream in)
        throws IOException {

        if (!in.markSupported()) return null;
        in.mark(MARK_READ_LIMIT);

        int bomLength = 0;
        String encoding = IOUtils.findBOMEncoding(contents);
        if (encoding == null) {
            encoding = "UTF-8";
        } else {
            bomLength = IOUtils.skipForBOM(contents);
            if (in.skip(bomLength) != bomLength) {
                in.reset();
                return null;
            }
        }

        BufferedReader rdr = new BufferedReader(new InputStreamReader(
            in, encoding));

        // Before reading a line, read some characters for a first look
        char[] buf = new char[FIRST_LOOK_WIDTH];
        int lenFirstLook;
        if ((lenFirstLook = rdr.read(buf)) < 1) {
            in.reset();
            return null;
        }

        // Require a "." or "'" as the first non-whitespace character after
        // only a limited number of whitespaces or else infer it is not troff
        // or mandoc.
        int actualFirstContentWidth = lenFirstLook < FIRST_CONTENT_WIDTH ?
            lenFirstLook : FIRST_CONTENT_WIDTH;
        boolean foundContent = false;
        for (int i = 0; i < actualFirstContentWidth; ++i) {
            if (buf[i] == '.' || buf[i] == '\'') {
                foundContent = true;
                break;
            } else if (!Character.isWhitespace(buf[i])) {
                in.reset();
                return null;
            }
        }
        if (!foundContent) {
            in.reset();
            return null;
        }

        // affirm that a LF is seen in the first look or else quickly
        // infer it is not troff
        boolean foundLF = false;
        for (int i = 0; i < lenFirstLook; ++i) {
            if (buf[i] == '\n') {
                foundLF = true;
                break;
            }
        }
        if (!foundLF) {
            in.reset();
            return null;
        }

        // reset for line-by-line reading below
        in.reset();
        if (bomLength > 0) in.skip(bomLength);
        rdr = new BufferedReader(new InputStreamReader(in, encoding));

        int numLines = 0;
        String line;
        while ((line = rdr.readLine()) != null) {
            for (int i = 0; i < lineStarters.length; ++i) {
                if (line.startsWith(lineStarters[i])) {
                    in.reset();
                    return factory;
                }
            }
            if (++numLines >= LINE_LIMIT) {
                in.reset();
                return null;
            }
        }

        in.reset();
        return null;
    }
}
