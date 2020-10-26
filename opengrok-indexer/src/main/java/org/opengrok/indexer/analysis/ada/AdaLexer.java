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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.ada;

import java.io.IOException;

import org.opengrok.indexer.analysis.JFlexJointLexer;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.Resettable;

/**
 * Represents an abstract base class for Ada lexers.
 */
abstract class AdaLexer extends JFlexSymbolMatcher
        implements JFlexJointLexer, Resettable {

    /**
     * Writes {@code value} to output -- if it contains any EOLs then the
     * {@link JFlexJointLexer#startNewLine()} is called in lieu of outputting
     * EOL.
     */
    public void takeLiteral(String value, String className)
            throws IOException {

        disjointSpan(className);

        int off = 0;
        do {
            int w = 1, ri, ni, i;
            ri = value.indexOf("\r", off);
            ni = value.indexOf("\n", off);
            if (ri == -1 && ni == -1) {
                String sub = value.substring(off);
                offer(sub);
                break;
            }
            if (ri != -1 && ni != -1) {
                if (ri < ni) {
                    i = ri;
                    if (value.charAt(ri) == '\r' && value.charAt(ni) == '\n') {
                        w = 2;
                    }
                } else {
                    i = ni;
                }
            } else if (ri != -1) {
                i = ri;
            } else {
                i = ni;
            }

            String sub = value.substring(off, i);
            offer(sub);
            disjointSpan(null);
            startNewLine();
            disjointSpan(className);
            off = i + w;
        } while (off < value.length());

        disjointSpan(null);
    }

    /**
     * Calls {@link #phLOC()} if the yystate is not SCOMMENT.
     */
    public void chkLOC() {
        if (yystate() != SCOMMENT()) {
            phLOC();
        }
    }

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent SCOMMENT.
     */
    abstract int SCOMMENT();
}
