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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.opengrok.indexer.analysis.StreamSource;

/**
 * Represents a splitter of source text into lines, where end-of-line tokens --
 * in accordance with {@link StringUtils#STANDARD_EOL} -- are maintained instead
 * of being stripped.
 */
public class SourceSplitter {

    private int length;
    private String[] lines;
    private int[] lineOffsets;

    /**
     * Gets the number of characters in the original source document.
     */
    public int originalLength() {
        return length;
    }

    /**
     * Gets the number of split lines.
     */
    public int count() {
        if (lines == null) {
            throw new IllegalStateException("reset() did not succeed");
        }
        return lines.length;
    }

    /**
     * Gets the line at the specified index in the lines list.
     * @param index greater than or equal to zero and less than
     * {@link #count()}
     * @return defined instance
     * @throws IllegalArgumentException if {@code index} is out of bounds
     */
    public String getLine(int index) {
        if (lines == null) {
            throw new IllegalStateException("reset() did not succeed");
        }
        if (index < 0 || index >= lines.length) {
            throw new IllegalArgumentException("index is out of bounds");
        }
        return lines[index];
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

    /**
     * Reset the splitter to use the specified content.
     * @param original a defined instance
     */
    public void reset(String original) {
        if (original == null) {
            throw new IllegalArgumentException("`original' is null");
        }

        try {
            reset(new StringReader(original));
        } catch (IOException ex) {
            /*
             * Should not get here, as String and StringReader operations cannot
             * throw IOException.
             */
            throw new RuntimeException(ex);
        }
    }

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
     * Reset the splitter to use the specified inputs.
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
        lines = null;
        lineOffsets = null;

        List<String> slist = new ArrayList<>();
        SourceSplitterScanner scanner = new SourceSplitterScanner(reader);
        scanner.setTarget(slist);
        scanner.consume();
        long fullLength = scanner.getLength();
        /*
         * Lucene cannot go past Integer.MAX_VALUE so revise the length to fit
         * within the Integer constraint.
         */
        length = fullLength > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fullLength;

        lines = slist.toArray(new String[0]);
        setLineOffsets();
    }

    private void setLineOffsets() {
        /*
         * Add one more entry for lineOffsets so that findLineIndex() can
         * easily work on the last line.
         */
        lineOffsets = new int[lines.length + 1];
        int offset = 0;
        for (int i = 0; i < lineOffsets.length; ++i) {
            lineOffsets[i] = offset;
            if (i < lines.length) {
                offset += lines[i].length();
            }
        }
    }
}
