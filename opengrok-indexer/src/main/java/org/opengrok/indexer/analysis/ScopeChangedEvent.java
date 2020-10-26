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
 * Represents an event raised when a language lexer indicates that scope has
 * changed.
 */
public class ScopeChangedEvent {

    private final Object source;
    private final ScopeAction action;
    private final String str;
    private final long start;
    private final long end;

    /**
     * Initializes an immutable instance of {@link ScopeChangedEvent}.
     * @param source the event source
     * @param action the scope change action
     * @param str the text string
     * @param start the text start position
     * @param end the text end position
     */
    public ScopeChangedEvent(Object source, ScopeAction action, String str, long start, long end) {
        this.source = source;
        this.action = action;
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
     * Gets the scope change action.
     * @return the initial value
     */
    public ScopeAction getAction() {
        return action;
    }

    /**
     * Gets the text string.
     * @return the text string
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
}
