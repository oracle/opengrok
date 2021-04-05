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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Perl file
 */

package org.opengrok.indexer.analysis.perl;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class PerlXref
%extends PerlLexer
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
    @Override
    public void offer(String value) throws IOException {
        onNonSymbolMatched(value, yychar);
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset,
            boolean ignoreKwd) throws IOException {
        Set<String> keywords = ignoreKwd ? null : Consts.kwd;
        return onFilteredSymbolMatched(value, yychar, keywords);
    }

    @Override
    public void skipSymbol() {
        // noop
    }

    @Override
    public void offerKeyword(String value) throws IOException {
        onKeywordMatched(value, yychar);
    }

    @Override
    public void startNewLine() throws IOException {
        onEndOfLineMatched("\n", yychar);
    }

    @Override
    public void disjointSpan(String className) throws IOException {
        onDisjointSpanChanged(className, yychar);
    }

    @Override
    public void abortQuote() throws IOException {
        yypop();
        if (areModifiersOK()) {
            yypush(QM);
        }
        onDisjointSpanChanged(null, yychar);
    }

    // If the state is YYINITIAL, then transitions to INTRA; otherwise does
    // nothing, because other transitions would have saved the state.
    public void maybeIntraState() {
        if (yystate() == YYINITIAL) {
            yybegin(INTRA);
        }
    }

    protected boolean takeAllContent() {
        return true;
    }

    protected boolean returnOnSymbol() {
        return false;
    }

    protected void skipLink(String s, Pattern p) { /* noop */ }

    /**
     * Gets the constant value created by JFlex to represent QUOxLxN.
     */
    @Override
    int QUOxLxN() { return QUOxLxN; }

    /**
     * Gets the constant value created by JFlex to represent QUOxN.
     */
    @Override
    int QUOxN() { return QUOxN; }

    /**
     * Gets the constant value created by JFlex to represent QUOxL.
     */
    @Override
    int QUOxL() { return QUOxL; }

    /**
     * Gets the constant value created by JFlex to represent QUO.
     */
    @Override
    int QUO() { return QUO; }

    /**
     * Gets the constant value created by JFlex to represent HEREinxN.
     */
    @Override
    int HEREinxN() { return HEREinxN; }

    /**
     * Gets the constant value created by JFlex to represent HERExN.
     */
    @Override
    int HERExN() { return HERExN; }

    /**
     * Gets the constant value created by JFlex to represent HEREin.
     */
    @Override
    int HEREin() { return HEREin; }

    /**
     * Gets the constant value created by JFlex to represent HERE.
     */
    @Override
    int HERE() { return HERE; }

    /**
     * Gets the constant value created by JFlex to represent SCOMMENT.
     */
    @Override
    int SCOMMENT() { return SCOMMENT; }

    /**
     * Gets the constant value created by JFlex to represent POD.
     */
    @Override
    int POD() { return POD; }
%}

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include PerlProductions.lexh
