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

import java.util.Objects;

/**
 * Represents an almost-wholly immutable tuple whose field data are used by
 * {@link JFlexTokenizer} to set attributes to be read while iterating a
 * token stream.
 */
public class PendingToken {

    public final String str;
    public final int start;
    public final int end;
    /**
     * When tokenizers allow overlapping tokens, the following field is set to
     * {@code true} for tokens that should not increment the position attribute.
     */
    public boolean nonpos;

    /**
     * Initializes an instance with immutable fields for the specified
     * arguments.
     * @param str string value
     * @param start start offset
     * @param end end offset
     */
    public PendingToken(String str, int start, int end) {
        this.str = str;
        this.start = start;
        this.end = end;
    }

    /**
     * Compares this instance's immutable fields to the other.
     * @param o object
     * @return {@code true} if the objects are equal
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PendingToken)) {
            return false;
        }
        PendingToken other = (PendingToken) o;
        return start == other.start && end == other.end &&
            str.equals(other.str);
    }

    /**
     * Calculates a hash code from the instance's immutable fields.
     * @return a hash value
     */
    @Override
    public int hashCode() {
        return Objects.hash(str, start, end);
    }

    /**
     * Gets a readable representation for debugging.
     * @return a defined instance
     */
    @Override
    public String toString() {
        return "PendingToken{" + str + "<<< start=" + start + ",end=" + end +
            ",nonpos=" + nonpos + '}';
    }
}
