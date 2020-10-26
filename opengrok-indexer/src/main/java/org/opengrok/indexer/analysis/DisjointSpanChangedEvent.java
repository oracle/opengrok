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
 * Represents an event raised when a language lexer indicates that a disjoint
 * span has changed.
 */
public class DisjointSpanChangedEvent {

    private final Object source;
    private final String className;
    private final long position;

    /**
     * Initializes an immutable instance of {@link DisjointSpanChangedEvent}.
     * @param source the event source
     * @param className the span class name
     * @param position the span position
     */
    public DisjointSpanChangedEvent(Object source, String className, long position) {
        this.source = source;
        this.className = className;
        this.position = position;
    }

    /**
     * Gets the event source.
     * @return the initial value
     */
    public Object getSource() {
        return source;
    }

    /**
     * Gets the span class name string.
     * @return the initial value
     */
    public String getDisjointSpanClassName() {
        return className;
    }

    /**
     * Gets the span position.
     * @return the initial value
     */
    public long getPosition() {
        return position;
    }
}
