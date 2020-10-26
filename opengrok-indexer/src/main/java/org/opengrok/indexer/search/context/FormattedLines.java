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
package org.opengrok.indexer.search.context;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;

/**
 * Represents structured results from {@link ContextFormatter} that can be
 * merged with other instances.
 * <p>
 * {@link UnifiedHighlighter} returns results separated by field, and
 * {@link OGKUnifiedHighlighter} merges them together to return a coherent
 * result for presentation.
 */
public class FormattedLines {
    private final SortedMap<Integer, String> lines = new TreeMap<>();
    private String footer;
    private boolean limited;

    /*
     * Gets a count of the number of lines in the instance.
     */
    public int getCount() {
        return lines.size();
    }

    /**
     * @return the footer
     */
    public String getFooter() {
        return footer;
    }

    public void setFooter(String value) {
        footer = value;
    }

    /*
     * Gets a value indicating if lines were limited.
     */
    public boolean isLimited() {
        return limited;
    }

    /*
     * Sets a value indicating if lines were limited.
     */
    public void setLimited(boolean value) {
        limited = value;
    }

    /**
     * Removes the highest line from the instance.
     * @return a defined value
     * @throws NoSuchElementException if the instance is empty
     */
    public String pop() {
        return lines.remove(lines.lastKey());
    }

    /**
     * Sets the specified String line for the specified line number, replacing
     * any previous entry for the same line number.
     * @param lineno a value
     * @param line a defined instance
     * @return the former value or {@code null}
     */
    public String put(int lineno, String line) {
        if (line == null) {
            throw new IllegalArgumentException("line is null");
        }
        return lines.put(lineno, line);
    }

    /**
     * Creates a new instance with lines merged from this instance and
     * {@code other}. Any lines in common for the same line number are taken
     * from this instance rather than {@code other}; and likewise for
     * {@link #getFooter()}.
     * <p>
     * {@link #isLimited()} will be {@code true} if either is {@code true}, but
     * the value is suspect since it cannot be truly known if the merged result
     * is actually the unlimited result.
     * @param other a defined instance
     * @return a defined instance
     */
    public FormattedLines merge(FormattedLines other) {
        FormattedLines res = new FormattedLines();
        res.lines.putAll(this.lines);
        for (Map.Entry<Integer, String> kv : other.lines.entrySet()) {
            res.lines.putIfAbsent(kv.getKey(), kv.getValue());
        }

        res.setFooter(this.footer != null ? this.footer : other.footer);
        res.setLimited(this.limited || other.limited);
        return res;
    }

    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder();
        for (String line : lines.values()) {
            bld.append(line);
        }
        String f = footer;
        if (f != null && limited) {
            bld.append(f);
        }
        return bld.toString();
    }
}
