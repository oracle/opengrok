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
 * Copyright (c) 2026, nishank.soni <soninishank8@gmail.com>.
 */
package org.opengrok.indexer.analysis.yang;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexJointLexer;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.Resettable;

/**
 * Base class for YANG lexers.
 */
public abstract class YangLexer extends JFlexSymbolMatcher
        implements JFlexJointLexer, Resettable {

    /**
     * Calls {@link #phLOC()} if the scanner is not in a comment state.
     */
    public void chkLOC() {
        int state = yystate();
        if (state != COMMENT() && state != SCOMMENT()) {
            phLOC();
        }
    }

    public abstract void offer(String value) throws IOException;

    public abstract boolean offerSymbol(String value, int captureOffset, boolean ignoreKwd)
            throws IOException;

    public abstract void skipSymbol();

    public abstract void offerKeyword(String value) throws IOException;

    public abstract void startNewLine() throws IOException;

    public abstract void disjointSpan(String className) throws IOException;

    public abstract void phLOC();

    protected abstract boolean takeAllContent();

    protected abstract boolean returnOnSymbol();

    public abstract int COMMENT();

    public abstract int SCOMMENT();
}
