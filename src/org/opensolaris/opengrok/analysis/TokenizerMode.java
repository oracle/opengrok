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

package org.opensolaris.opengrok.analysis;

/**
 * Represents an enumeration of token-production modes.
 */
public enum TokenizerMode {
    /**
     * Only produces tokens raised via {@link SymbolMatchedEvent}.
     */
    SYMBOLS_ONLY,
    /**
     * Only produces tokens resulting from analysis of contiguous, disjoint
     * non-whitespace.
     */
    NON_WHITESPACE_ONLY,
    /**
     * Produces tokens raised by {@link SymbolMatchedEvent} -- as well as those
     * resulting from analysis of contiguous, disjoint non-whitespace plus
     * word-boundary and opening- and closing-punctuation boundary sub-strings
     * found therein.
     */
    SYMBOLS_AND_NON_WHITESPACE
}
