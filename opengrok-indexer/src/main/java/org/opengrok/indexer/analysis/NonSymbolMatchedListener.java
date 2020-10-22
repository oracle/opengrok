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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

/**
 * Represents an API for a listener for non-symbolic or non-indexed symbol
 * matching events.
 */
public interface NonSymbolMatchedListener {

    /**
     * Receives an event instance.
     * @param evt the event
     */
    void nonSymbolMatched(TextMatchedEvent evt);

    /**
     * Receives an event instance.
     * @param evt the event
     */
    void keywordMatched(TextMatchedEvent evt);

    /**
     * Receives an event instance.
     * @param evt the event
     */
    void endOfLineMatched(TextMatchedEvent evt);

    /**
     * Receives an event instance.
     * @param evt the event
     */
    void disjointSpanChanged(DisjointSpanChangedEvent evt);

    /**
     * Receives an event instance.
     * @param evt the event
     */
    void linkageMatched(LinkageMatchedEvent evt);

    /**
     * Receives an event instance.
     * @param evt the event
     */
    void pathlikeMatched(PathlikeMatchedEvent evt);

    /**
     * Receives an event instance.
     * @param evt the event
     */
    void scopeChanged(ScopeChangedEvent evt);
}
