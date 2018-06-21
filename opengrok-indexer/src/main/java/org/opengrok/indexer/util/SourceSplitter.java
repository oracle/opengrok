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
import java.util.regex.Matcher;
import org.opengrok.indexer.analysis.StreamSource;

/**
 * Represents a splitter of source text into lines where end-of-line tokens --
 * identified by {@link StringUtils#STANDARD_EOL} -- are maintained instead of
 * being stripped.
 */
public class SourceSplitter {

    private int length;
    private String[] lines;
    private int[] lineOffsets;

    /**
     * Gets the number of characters in the original source document.
     * @return value
     */
    public int originalLength() {
        return length;
    }

    /**
     * Gets the number of split lines.
     * @return value
     */
    public int count() {
        if (lines == null) {
            throw new IllegalStateException("reset() has not succeeded");
        }
        return lines.length;
    }

    /**
     * Gets the lines at the specified offset.
     * @param offset greater than or equal to zero and less than
     * {@link #count()}
     * @return defined instance
     * @throws IllegalArgumentException if {@code offset} is out of bounds
     */
    public String getLine(int offset) {
        if (lines == null) {
            throw new IllegalStateException("reset() has not succeeded");
        }
        if (offset < 0 || offset >= lines.length) {
            throw new IllegalArgumentException("`offset' is out of bounds");
        }
        return lines[offset];
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
        if (lineOffsets == null) {
            throw new IllegalStateException("reset() has not succeeded");
        }
        if (offset < 0 || offset >= lineOffsets.length) {
            throw new IllegalArgumentException("`offset' is out of bounds");
        }
        return lineOffsets[offset];
    }

    /**
     * Find the line offset for the specified document position.
     * @param position greater than or equal to zero and less than
     * {@link #originalLength()}.
     * @return -1 if {@code position} is beyond the document bounds; otherwise,
     * a valid offset
     */
    public int findLineOffset(int position) {
        if (lineOffsets == null) {
            throw new IllegalStateException("reset() has not succeeded");
        }
        if (position < 0 || position > length) {
            return -1;
        }

        int lo = 0;
        int hi = lineOffsets.length - 1;
        int mid;
        while (lo <= hi) {
            mid = lo + (hi - lo) / 2;
            int linelen = mid < lines.length ? lines[mid].length() : 0;
            if (position < lineOffsets[mid]) {
                hi = mid - 1;
            } else if (linelen == 0 && position == lineOffsets[mid]) {
                return mid;
            } else if (position >= lineOffsets[mid] + linelen) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    /**
     * Reset the splitter to use the specified content.
     * @param original a defined instance
     */
    public void reset(String original) {
        if (original == null) {
            throw new IllegalArgumentException("`original' is null");
        }

        length = original.length();
        lines = null;
        lineOffsets = null;

        List<String> slist = new ArrayList<>();
        int position = 0;
        Matcher eolm = StringUtils.STANDARD_EOL.matcher(original);
        while (eolm.find()) {
            slist.add(original.substring(position, eolm.end()));
            position = eolm.end();
        }
        if (position < original.length()) {
            slist.add(original.substring(position));
        } else {
            /*
             * Following JFlexXref's custom, an empty file or a file ending
             * with LF produces an additional line of length zero.
             */
            slist.add("");
        }

        lines = slist.stream().toArray(String[]::new);
        setLineOffsets();
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

        length = 0;
        lines = null;
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
        List<String> slist = new ArrayList<>();
        StringBuilder bld = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            ++length;
            bld.append((char)c);
            switch (c) {
                case '\r':
                    c = reader.read();
                    if (c == -1) {
                        slist.add(bld.toString());
                        bld.setLength(0);
                        break;
                    } else {
                        ++length;
                        switch (c) {
                            case '\n':
                                bld.append((char)c);
                                slist.add(bld.toString());
                                bld.setLength(0);
                                break;
                            case '\r':
                                slist.add(bld.toString());
                                bld.setLength(0);

                                bld.append((char)c);
                                slist.add(bld.toString());
                                bld.setLength(0);
                                break;
                            default:
                                slist.add(bld.toString());
                                bld.setLength(0);

                                bld.append((char)c);
                                break;
                        }
                    }
                    break;
                case '\n':
                    slist.add(bld.toString());
                    bld.setLength(0);
                    break;
                default:
                    break;
            }
        }
        if (bld.length() > 0) {
            slist.add(bld.toString());
            bld.setLength(0);
        } else {
            /*
             * Following JFlexXref's custom, an empty file or a file ending
             * with LF produces an additional line of length zero.
             */
            slist.add("");
        }

        lines = slist.stream().toArray(String[]::new);
        setLineOffsets();
    }

    private void setLineOffsets() {
        /**
         * Add one more entry for lineOffsets so that findLineOffset() can
         * easily work on the last line.
         */
        lineOffsets = new int[lines.length + 1];
        int position = 0;
        for (int i = 0; i < lineOffsets.length; ++i) {
            lineOffsets[i] = position;
            if (i < lines.length) {
                position += lines[i].length();
            }
        }
    }
}
