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
package org.opengrok.indexer.analysis.verilog;

import org.opengrok.indexer.analysis.JFlexJointLexer;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.Resettable;

/**
 * Represents an abstract base class for SystemVerilog lexers.
 */
@SuppressWarnings("Duplicates")
abstract class VerilogLexer extends JFlexSymbolMatcher
        implements JFlexJointLexer, Resettable {

    /**
     * Calls {@link #phLOC()} if the yystate is not COMMENT or SCOMMENT.
     */
    public void chkLOC() {
        if (yystate() != COMMENT() && yystate() != SCOMMENT()) {
            phLOC();
        }
    }

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent COMMENT.
     */
    abstract int COMMENT();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent SCOMMENT.
     */
    abstract int SCOMMENT();
}
