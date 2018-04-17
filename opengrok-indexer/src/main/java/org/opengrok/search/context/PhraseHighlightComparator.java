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

package org.opengrok.search.context;

import java.util.Comparator;

/**
 * Represents a {@link Comparator} for {@link PhraseHighlight}.
 */
public class PhraseHighlightComparator implements Comparator<PhraseHighlight> {

    public static final PhraseHighlightComparator INSTANCE =
        new PhraseHighlightComparator();

    @Override
    public int compare(PhraseHighlight o1, PhraseHighlight o2) {
        // ASC by lineStart, with -1 == -Inf.
        if (o1.getLineStart() < 0) {
            if (o2.getLineStart() >= 0) {
                return -1;
            }
        } else if (o2.getLineStart() < 0) {
            return 1;
        }
        int cmp = Integer.compare(o1.getLineStart(), o2.getLineStart());
        if (cmp != 0) {
            return cmp;
        }
        // DESC by lineEnd, with -1 == Inf.
        cmp = Integer.compare(o2.getLineEnd(), o1.getLineEnd());
        return cmp;
    }

    /** private to enforce singleton */
    private PhraseHighlightComparator() {
    }
}
