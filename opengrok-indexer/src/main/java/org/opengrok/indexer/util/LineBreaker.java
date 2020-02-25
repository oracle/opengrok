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
 * Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import java.io.IOException;
import java.io.Reader;
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
    private int count;
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

        SplitterUtil.reset(this::reset, src, wrapper);
    }

    private void reset(Reader reader) throws IOException {
        length = 0;
        lineOffsets = null;

        List<Integer> newOffsets = new ArrayList<>();
        newOffsets.add(0);

        int c;
        while ((c = reader.read()) != -1) {
            ++length;

            redo_c:
            while (true) {
                switch (c) {
                    case '\r':
                        c = reader.read();
                        if (c == -1) {
                            newOffsets.add(length);
                            break redo_c;
                        }
                        ++length;
                        if (c == '\n') {
                            newOffsets.add(length);
                            break redo_c;
                        }
                        newOffsets.add(length - 1);
                        continue redo_c;
                    case '\n':
                        newOffsets.add(length);
                        break redo_c;
                    default:
                        // pass
                }
                break;
            }
        }

        count = newOffsets.size();
        if (newOffsets.get(newOffsets.size() - 1) < length) {
            newOffsets.add(length);
            // Do not increment count.
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
     * Gets the number of split lines.
     */
    public int count() {
        if (lineOffsets == null) {
            throw new IllegalStateException("reset() did not succeed");
        }
        return count;
    }

    /**
     * Gets the starting document character offset of the line at the
     * specified index in the lines list.
     * @param index greater than or equal to zero and less than or equal to
     * {@link #count()}
     * @return line starting offset
     * @throws IllegalArgumentException if {@code index} is out of bounds
     */
    public int getOffset(int index) {
        if (lineOffsets == null) {
            throw new IllegalStateException("reset() did not succeed");
        }
        if (index < 0 || index >= lineOffsets.length) {
            throw new IllegalArgumentException("index is out of bounds");
        }
        return lineOffsets[index];
    }

    /**
     * Find the line index for the specified document offset.
     * @param offset greater than or equal to zero and less than
     * {@link #originalLength()}.
     * @return -1 if {@code offset} is beyond the document bounds; otherwise,
     * a valid index
     */
    public int findLineIndex(int offset) {
        if (lineOffsets == null) {
            throw new IllegalStateException("reset() did not succeed");
        }
        return SplitterUtil.findLineIndex(length, lineOffsets, offset);
    }
}
