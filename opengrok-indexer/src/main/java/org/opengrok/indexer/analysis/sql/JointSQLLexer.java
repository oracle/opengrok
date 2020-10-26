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

import org.opengrok.indexer.analysis.JFlexJointLexer;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.Resettable;

import java.io.IOException;
import java.util.Set;

/**
 * Represents an abstract base class for SQL lexers of various dialects.
 */
abstract class JointSQLLexer extends JFlexSymbolMatcher implements JFlexJointLexer, Resettable {

    protected int commentLevel;

    @Override
    public void reset() {
        super.reset();
        commentLevel = 0;
    }

    @Override
    public void yypop() throws IOException {
        onDisjointSpanChanged(null, getYYCHAR());
        super.yypop();
    }

    /**
     * Calls {@link #phLOC()} if the yystate is not BRACKETED_COMMENT or
     * SINGLE_LINE_COMMENT.
     */
    public void chkLOC() {
        if (yystate() != BRACKETED_COMMENT() && yystate() != SINGLE_LINE_COMMENT()) {
            phLOC();
        }
    }

    /**
     * Subclasses must override to get the dialect's keywords set.
     */
    abstract Set<String> getDialectKeywords();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent BRACKETED_COMMENT.
     */
    abstract int BRACKETED_COMMENT();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent SINGLE_LINE_COMMENT.
     */
    abstract int SINGLE_LINE_COMMENT();
}
