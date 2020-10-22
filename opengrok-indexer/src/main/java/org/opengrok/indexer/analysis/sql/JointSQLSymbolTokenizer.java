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

import java.util.Locale;

/**
 * Represents an abstract base class for SQL symbol tokenizers of various
 * dialects.
 */
abstract class JointSQLSymbolTokenizer extends JointSQLLexer {

    private String lastSymbol;

    /**
     * Resets the SQL tracked state. {@inheritDoc}
     */
    @Override
    public void reset() {
        super.reset();
        lastSymbol = null;
    }

    /** noop. */
    @Override
    public void offer(String value) {
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset, boolean ignoreKwd) {
        if (ignoreKwd || !getDialectKeywords().contains(value.toLowerCase(Locale.ROOT))) {
            lastSymbol = value;
            onSymbolMatched(value, getYYCHAR() + captureOffset);
            return true;
        } else {
            lastSymbol = null;
        }
        return false;
    }

    @Override
    public void skipSymbol() {
        lastSymbol = null;
    }

    @Override
    public void offerKeyword(String value) {
        lastSymbol = null;
    }

    /** noop. */
    @Override
    public void startNewLine() {
    }

    /** noop. */
    @Override
    public void disjointSpan(String className) {
    }

    /** noop. */
    @Override
    public void phLOC() {
    }

    /** Gets the value {@code false}. */
    protected boolean takeAllContent() {
        return false;
    }

    /** Gets the value indicating if {@link #lastSymbol} is defined. */
    protected boolean returnOnSymbol() {
        return lastSymbol != null;
    }
}
