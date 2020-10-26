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

/**
 * Represents a highlighted phrase within a line -- possibly with bounds
 * indicating that the highlight begins or ends on another line.
 */
public class PhraseHighlight {

    /**
     * a value that has been translated from start offset w.r.t. document
     * start to a value w.r.t. line start -- or -1 if not beginning this
     * line
     */
    private final int lineStart;
    /**
     * a value that has been translated from start offset w.r.t. document
     * start to a value w.r.t. line start -- or {@link Integer#MAX_VALUE} if
     * not ending this line
     */
    private final int lineEnd;

    public static PhraseHighlight create(int start, int end) {
        return new PhraseHighlight(start, end);
    }

    public static PhraseHighlight createStarter(int start) {
        return new PhraseHighlight(start, Integer.MAX_VALUE);
    }

    public static PhraseHighlight createEnder(int end) {
        return new PhraseHighlight(-1, end);
    }

    public static PhraseHighlight createEntire() {
        return new PhraseHighlight(-1, Integer.MAX_VALUE);
    }

    /**
     * Gets a value that has been translated from start offset w.r.t. document
     * start to a value w.r.t. line start -- or -1 if not beginning this
     * line.
     * @return offset
     */
    public int getLineStart() {
        return lineStart;
    }

    /**
     * Gets a value that has been translated from start offset w.r.t. document
     * start to a value w.r.t. line start -- or {@link Integer#MAX_VALUE} if
     * not ending this line.
     * @return offset
     */
    public int getLineEnd() {
        return lineEnd;
    }

    /**
     * Determines if the specified {@code other} overlaps with this instance.
     * @return {@code true} if the instances overlap
     * @param other object to compare to
     */
    public boolean overlaps(PhraseHighlight other) {
        return (lineStart >= other.lineStart && lineStart <= other.lineEnd) ||
            (other.lineStart >= lineStart && other.lineStart <= lineEnd) ||
            (lineEnd >= other.lineStart && lineEnd <= other.lineEnd) ||
            (other.lineEnd >= lineStart && other.lineEnd <= lineEnd);
    }

    /**
     * Creates a new instance that is the merging of this instance and the
     * specified {@code other}.
     * @param other object to compare to
     * @return a defined instance
     */
    public PhraseHighlight merge(PhraseHighlight other) {
        int mergeStart = Math.min(lineStart, other.lineStart);
        int mergeEnd = Math.max(lineEnd, other.lineEnd);
        return PhraseHighlight.create(mergeStart, mergeEnd);
    }

    /** Private to enforce static create() methods. */
    private PhraseHighlight(int start, int end) {
        this.lineStart = start;
        this.lineEnd = end;
    }
}
