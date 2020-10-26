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
 * Represents an immutable settings instance for coordinating objects related
 * to producing context presentations.
 */
public class ContextArgs {
    /** Not Lucene-related, so {@code int} does fine. */
    private static final int CONTEXT_WIDTH = 100;

    /** Lucene uhighlight-related, so {@code short} is safer. */
    private final short contextSurround;

    /** Lucene uhighlight-related, so {@code short} is safer. */
    private final short contextLimit;

    /**
     * Initializes an instance with the specified values.
     * <p>
     * {@code short} is used because some Lucene classes were found to choke
     * when OpenGrok used {@link Integer#MAX_VALUE} to mean "unbounded".
     * {@code short} is safer therefore but unfortunately somewhat syntactically
     * inconvenient.
     * @param contextSurround a non-negative value
     * @param contextLimit a positive value
     */
    public ContextArgs(short contextSurround, short contextLimit) {
        if (contextSurround < 0) {
            throw new IllegalArgumentException(
                "contextSurround cannot be negative");
        }
        if (contextLimit < 1) {
            throw new IllegalArgumentException(
                "contextLimit must be positive");
        }
        this.contextSurround = contextSurround;
        this.contextLimit = contextLimit;
    }

    /**
     * Gets the number of lines of leading and trailing context surrounding each
     * match line to present.
     * <p>
     * (N.b. the value is used w.r.t. {@link #getContextLimit()} and therefore
     * w.r.t. Lucene {@code uhighlight}, and {@code short} is safer, though
     * syntactically inconvenient, to avoid numeric overlow that may occur with
     * {@code int} in that library.)
     * @return a non-negative value
     */
    public short getContextSurround() {
        return contextSurround;
    }

    /**
     * Gets the maximum number of lines to present, after which a "more..." link
     * is displayed to allow the user to view full match results.
     * <p>
     * (N.b. the value is used with Lucene {@code uhighlight}, and {@code short}
     * is safer, though syntactically inconvenient, to avoid numeric overlow
     * that may occur with {@code int} in that library.)
     * @return a positive value
     */
    public short getContextLimit() {
        return contextLimit;
    }

    /**
     * Gets a value indicating the maximum width to show for lines in a context
     * presentation.
     * @return a positive value
     */
    public int getContextWidth() {
        return CONTEXT_WIDTH;
    }
}
