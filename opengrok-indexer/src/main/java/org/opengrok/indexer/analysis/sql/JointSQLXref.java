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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.sql;

import java.util.Set;

/**
 * Represents an abstract base class for SQL xrefers of various dialects.
 */
abstract class JointSQLXref extends JointSQLLexer {

    @Override
    public void offer(String value) {
        onNonSymbolMatched(value, getYYCHAR());
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset, boolean ignoreKwd) {
        Set<String> keywords = ignoreKwd ? null : getDialectKeywords();
        return onFilteredSymbolMatched(value, getYYCHAR(), keywords, false);
    }

    /** noop. */
    @Override
    public void skipSymbol() {
    }

    @Override
    public void offerKeyword(String value) {
        onKeywordMatched(value, getYYCHAR());
    }

    @Override
    public void startNewLine() {
        onEndOfLineMatched("\n", getYYCHAR());
    }

    @Override
    public void disjointSpan(String className) {
        onDisjointSpanChanged(className, getYYCHAR());
    }

    /** Gets the value {@code true}. */
    protected boolean takeAllContent() {
        return true;
    }

    /** Gets the value {@code false}. */
    protected boolean returnOnSymbol() {
        return false;
    }
}
