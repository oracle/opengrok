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
package org.opengrok.indexer.analysis;

import java.util.Comparator;

/**
 * Represents a comparator for {@link PendingToken} that just compares the
 * instances' offsets.
 */
public class PendingTokenOffsetsComparator implements Comparator<PendingToken> {

    /**
     * Singleton instance.
     */
    public static final PendingTokenOffsetsComparator INSTANCE =
        new PendingTokenOffsetsComparator();

    @Override
    public int compare(PendingToken o1, PendingToken o2) {
        // ASC by starting offset.
        int cmp = Integer.compare(o1.start, o2.start);
        if (cmp != 0) {
            return cmp;
        }
        // ASC by ending offset
        cmp = Integer.compare(o1.end, o2.end);
        return cmp;
    }

    /** Private to enforce singleton. */
    private PendingTokenOffsetsComparator() {
    }
}
