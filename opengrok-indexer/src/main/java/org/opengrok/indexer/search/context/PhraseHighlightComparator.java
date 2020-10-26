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

import java.util.Comparator;

/**
 * Represents a {@link Comparator} for {@link PhraseHighlight}.
 */
public class PhraseHighlightComparator implements Comparator<PhraseHighlight> {

    public static final PhraseHighlightComparator INSTANCE = new PhraseHighlightComparator();

    /**
     * Compares two {@link PhraseHighlight} instances for order by first
     * comparing using {@link Integer#compare(int, int)} the
     * {@link PhraseHighlight#lineStart} values of {@code o1} and {@code o2} and
     * subsequently, if identical, comparing the {@link PhraseHighlight#lineEnd}
     * values of {@code o2} and {@code o1} (i.e. inverted).
     * <p>The ordering allows to iterate through a collection afterward and
     * easily subsume where necessary a {@link PhraseHighlight} instance into
     * its immediate predecessor.
     * @param o1 a required instance
     * @param o2 a required instance
     * @return a negative integer, zero, or a positive integer as the first
     * argument is less than, equal to, or greater than the second
     */
    @Override
    public int compare(PhraseHighlight o1, PhraseHighlight o2) {
        int cmp;
        if (o1.getLineStart() < 0) {
            if (o2.getLineStart() >= 0) {
                return -1;
            }
            cmp = 0;
        } else if (o2.getLineStart() < 0) {
            return 1;
        } else {
            cmp = Integer.compare(o1.getLineStart(), o2.getLineStart());
        }
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(o2.getLineEnd(), o1.getLineEnd());
        return cmp;
    }

    /** Private to enforce singleton. */
    private PhraseHighlightComparator() {
    }
}
