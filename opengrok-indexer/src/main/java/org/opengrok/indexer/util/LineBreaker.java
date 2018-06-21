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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.opengrok.indexer.analysis.StreamSource;

/**
 * Represents a reader of source text to find end-of-line tokens -- in
 * accordance with {@link StringUtils#STANDARD_EOL} -- in order to determine
 * line offsets but discarding line content.
 */
public class LineBreaker {

    private int length;
    private int[] lineOffsets;

    /**
     * Calls
     * {@link #reset(org.opengrok.indexer.analysis.StreamSource, org.opengrok.indexer.util.ReaderWrapper)}
     * with {@code src} and {@code null}.
     * @param src a defined instance
     * @throws java.io.IOException if an I/O error occurs
     */
    public void reset(StreamSource src) throws IOException {
        reset(src, null);
    }

    /**
     * Resets the breaker using the specified inputs.
     * @param src a defined instance
     * @param wrapper an optional instance
     * @throws java.io.IOException if an I/O error occurs
     */
    public void reset(StreamSource src, ReaderWrapper wrapper)
            throws IOException {
        if (src == null) {
            throw new IllegalArgumentException("`src' is null");
        }

        length = 0;
        lineOffsets = null;

        try (InputStream in = src.getStream();
            Reader rdr = IOUtils.createBOMStrippedReader(in,
                StandardCharsets.UTF_8.name())) {
            Reader intermediate = null;
            if (wrapper != null) {
                intermediate = wrapper.get(rdr);
            }

            try (BufferedReader brdr = new BufferedReader(
                    intermediate != null ? intermediate : rdr)) {
                reset(brdr);
            } finally {
                if (intermediate != null) {
                    intermediate.close();
                }
            }
        }
    }

    private void reset(Reader reader) throws IOException {
        List<Integer> newOffsets = new ArrayList<>();
        newOffsets.add(0);

        int c;
        while ((c = reader.read()) != -1) {
            ++length;
            switch (c) {
                case '\r':
                    c = reader.read();
                    if (c == -1) {
                        newOffsets.add(length);
                        break;
                    } else {
                        ++length;
                        switch (c) {
                            case '\n':
                                newOffsets.add(length);
                                break;
                            case '\r':
                                newOffsets.add(length - 1);
                                newOffsets.add(length);
                                break;
                            default:
                                newOffsets.add(length - 1);
                                break;
                        }
                    }
                    break;
                case '\n':
                    newOffsets.add(length);
                    break;
                default:
                    break;
            }
        }

        lineOffsets = new int[newOffsets.size()];
        for (int i = 0; i < lineOffsets.length; ++i) {
            lineOffsets[i] = newOffsets.get(i);
        }
    }

    /**
     * Gets the number of characters in the original source document.
     * @return value
     */
    public int originalLength() {
        return length;
    }

    /**
     * Gets the number of broken lines.
     * @return value
     */
    public int count() {
        if (lineOffsets == null) {
            throw new IllegalStateException("reset() did not succeed");
        }
        return lineOffsets.length;
    }

    /**
     * Gets the starting document character position of the line at the
     * specified offset.
     * @param offset greater than or equal to zero and less than or equal to
     * {@link #count()}
     * @return line length, including the end-of-line token
     * @throws IllegalArgumentException if {@code offset} is out of bounds
     */
    public int getPosition(int offset) {
        if (offset < 0 || lineOffsets == null || offset >= lineOffsets.length) {
            throw new IllegalArgumentException("`offset' is out of bounds");
        }
        return lineOffsets[offset];
    }
}
