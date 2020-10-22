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

// "How do I make a Class extend Observable when it has extended another class too?"
// Answered by adamski, https://stackoverflow.com/users/127479/adamski,
// https://stackoverflow.com/a/1658735/933163,
// https://stackoverflow.com/questions/1658702/how-do-i-make-a-class-extend-observable-when-it-has-extended-another-class-too

/**
 * Represents an event raised when a symbol matcher matches a string that
 * might be published as a symbol.
 */
public class SymbolMatchedEvent {

    private final Object source;
    private final String str;
    private final long start;
    private final long end;

    /**
     * Initializes an immutable instance of {@link SymbolMatchedEvent}.
     * @param source the event source
     * @param str the symbol string
     * @param start the symbol start position
     * @param end the symbol end position
     */
    public SymbolMatchedEvent(Object source, String str, long start, long end) {
        this.source = source;
        this.str = str;
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
     * Gets the symbol string.
     * @return the initial value
     */
    public String getStr() {
        return str;
    }

    /**
     * Gets the symbol start position.
     * @return the initial value
     */
    public long getStart() {
        return start;
    }

    /**
     * Gets the symbol end position.
     * @return the initial value
     */
    public long getEnd() {
        return end;
    }
}
