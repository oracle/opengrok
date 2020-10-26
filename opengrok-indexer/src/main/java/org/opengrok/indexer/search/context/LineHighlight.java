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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opengrok.indexer.web.HtmlConsts;
import org.opengrok.indexer.web.Util;

/**
 * Represents a collection of metadata related to highlighting a single line
 * of code.
 */
public class LineHighlight {

    private final int lineno;
    private List<PhraseHighlight> markups;
    /** Offset of elided left part. */
    private int lelide;
    /** Offset of elide right part. */
    private int relide;

    private boolean didLelide;
    private boolean didRelide;

    public LineHighlight(int lineno) {
        if (lineno < 0) {
            throw new IllegalArgumentException("lineno cannot be negative");
        }
        this.lineno = lineno;
    }

    /**
     * Gets the number of markups.
     * @return zero or greater
     */
    public int countMarkups() {
        return markups == null ? 0 : markups.size();
    }

    /**
     * Gets the highlight at the specified position.
     * @param i index of element to return
     * @return defined instance
     */
    public PhraseHighlight getMarkup(int i) {
        return markups.get(i);
    }

    /**
     * Sort and condense overlapping markup highlights.
     */
    public void condenseMarkups() {
        if (markups == null) {
            return;
        }

        markups.sort(PhraseHighlightComparator.INSTANCE);
        // Condense instances if there is overlap.
        for (int i = 0; i + 1 < markups.size(); ++i) {
            PhraseHighlight phi0 = markups.get(i);
            PhraseHighlight phi1 = markups.get(i + 1);
            if (phi0.overlaps(phi1)) {
                phi0 = phi0.merge(phi1);
                markups.set(i, phi0);
                markups.remove(i + 1);
                --i;
            }
        }
    }

    /**
     * Adds the specified highlight.
     * @param phi a defined instance
     */
    public void addMarkup(PhraseHighlight phi) {
        if (phi == null) {
            throw new IllegalArgumentException("phi is null");
        }
        if (markups == null) {
            markups = new ArrayList<>();
        }
        markups.add(phi);
    }

    /**
     * @return the lineno
     */
    public int getLineno() {
        return lineno;
    }

    /**
     * Gets the left elide value.
     * @return zero or greater
     */
    public int getLelide() {
        return lelide;
    }

    /**
     * Sets the left elide value.
     * @param value integer value
     */
    public void setLelide(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value is negative");
        }
        this.lelide = value;
    }

    /**
     * Gets the right elide value.
     * @return zero or greater
     */
    public int getRelide() {
        return relide;
    }

    /**
     * Sets the right elide value.
     * @param value integer value
     */
    public void setRelide(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value is negative");
        }
        this.relide = value;
    }

    /**
     * Append a substring with
     * {@link Util#htmlize(java.lang.CharSequence, java.lang.Appendable, boolean)},
     * taking into account any positive {@link #getLelide()} or
     * {@link #getRelide()}.
     * @param dest appendable object
     * @param line line value
     * @param start start offset
     * @param end end offset
     * @throws IOException I/O
     */
    public void hsub(Appendable dest, String line, int start, int end)
            throws IOException {
        boolean lell = false;
        boolean rell = false;
        if (start < lelide) {
            lell = true;
            start = lelide;
        }
        if (end < lelide) {
            end = lelide;
        }
        if (relide > 0) {
            if (start > relide) {
                start = relide;
            }
            if (end > relide) {
                rell = true;
                end = relide;
            }
        }
        String str = line.substring(start, end);
        if (lell && !didLelide) {
            dest.append(HtmlConsts.HELLIP);
            didLelide = true;
        }
        Util.htmlize(str, dest, true);
        if (rell && !didRelide) {
            dest.append(HtmlConsts.HELLIP);
            didRelide = true;
        }
    }

    /**
     * Calls {@link #hsub(java.lang.Appendable, java.lang.String, int, int)}
     * with {@code dest}, {@code line}, {@code loff}, and {@code line}
     * {@link String#length()}.
     * @param dest appendable object
     * @param line line value
     * @param loff start offset
     * @throws IOException I/O
     */
    public void hsub(Appendable dest, String line, int loff)
            throws IOException {
        hsub(dest, line, loff, line.length());
    }
}
