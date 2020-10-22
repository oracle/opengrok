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

import org.opengrok.indexer.analysis.StreamSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Represents a container for reusable splitter-oriented utility methods.
 */
class SplitterUtil {

    @FunctionalInterface
    interface Resetter {
        void reset(Reader reader) throws IOException;
    }

    /**
     * Find the line index for the specified document offset.
     * @param offset greater than or equal to zero and less than
     * {@code length}.
     * @return -1 if {@code offset} is beyond the document bounds; otherwise,
     * a valid index
     */
    static int findLineIndex(int length, int[] lineOffsets, int offset) {
        if (lineOffsets == null) {
            throw new IllegalArgumentException("lineOffsets");
        }
        if (offset < 0 || offset > length) {
            return -1;
        }

        int lo = 0;
        int hi = lineOffsets.length - 1;
        int mid;
        while (lo <= hi) {
            mid = lo + (hi - lo) / 2;
            int lineLength = (mid + 1 < lineOffsets.length ? lineOffsets[mid + 1] : length) -
                    lineOffsets[mid];
            if (offset < lineOffsets[mid]) {
                hi = mid - 1;
            } else if (lineLength == 0 && offset == lineOffsets[mid]) {
                return mid;
            } else if (offset >= lineOffsets[mid] + lineLength) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    /**
     * Resets the breaker using the specified inputs.
     * @param resetter a defined instance
     * @param src a defined instance
     * @param wrapper an optional instance
     * @throws java.io.IOException if an I/O error occurs
     */
    static void reset(Resetter resetter, StreamSource src, ReaderWrapper wrapper)
            throws IOException {
        if (src == null) {
            throw new IllegalArgumentException("src is null");
        }

        try (InputStream in = src.getStream();
             Reader rdr = IOUtils.createBOMStrippedReader(in, StandardCharsets.UTF_8.name())) {
            Reader intermediate = null;
            if (wrapper != null) {
                intermediate = wrapper.get(rdr);
            }

            try (BufferedReader brdr = new BufferedReader(intermediate != null ?
                    intermediate : rdr)) {
                resetter.reset(brdr);
            } finally {
                if (intermediate != null) {
                    intermediate.close();
                }
            }
        }
    }

    /* private to enforce static. */
    private SplitterUtil() {
    }
}
