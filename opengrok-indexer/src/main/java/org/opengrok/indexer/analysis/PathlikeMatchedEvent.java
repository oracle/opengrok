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
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

/**
 * Represents an event raised when a symbol matcher matches a path-like string
 * that would not be published as a symbol.
 */
public class PathlikeMatchedEvent {

    private final Object source;
    private final String str;
    private final char sep;
    private final boolean canonicalize;
    private final long start;
    private final long end;

    /**
     * Initializes an immutable instance of {@link PathlikeMatchedEvent}.
     * @param source the event source
     * @param str the path text string
     * @param sep the path separator
     * @param canonicalize a value indicating whether the path should be
     * canonicalized
     * @param start the text start position
     * @param end the text end position
     */
    public PathlikeMatchedEvent(Object source, String str, char sep,
        boolean canonicalize, long start, long end) {
        this.source = source;
        this.str = str;
        this.sep = sep;
        this.canonicalize = canonicalize;
        this.start = start;
        this.end = end;
    }

    /**
     * Gets the event source.
     * @return the initial value
     */
    public Object getSource() {
        return source;
    }

    /**
     * Gets the path text string.
     * @return the initial value
     */
    public String getStr() {
        return str;
    }

    /**
     * Gets the text start position.
     * @return the initial value
     */
    public long getStart() {
        return start;
    }

    /**
     * Gets the text end position.
     * @return the initial value
     */
    public long getEnd() {
        return end;
    }

    /**
     * Gets the path separator.
     * @return the initial value
     */
    public char getSep() {
        return sep;
    }

    /**
     * Gets a value indicating whether the path should be canonicalized.
     * @return the initial value
     */
    public boolean getCanonicalize() {
        return canonicalize;
    }
}
