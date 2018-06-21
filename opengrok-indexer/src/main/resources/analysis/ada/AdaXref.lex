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
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference an Ada file
 */

package org.opengrok.indexer.analysis.ada;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class AdaXref
%extends JFlexSymbolMatcher
%implements AdaLexer
%unicode
%ignorecase
%int
%char
%init{
    h = new AdaLexHelper(SCOMMENT, this);
    yyline = 1;
%init}
%include CommonLexer.lexh
%include CommonXref.lexh
%{
    private final AdaLexHelper h;

    /**
     * Resets the Ada tracked state; {@inheritDoc}
     */
    @Override
    public void reset() {
        super.reset();
        h.reset();
    }

    @Override
    public void offer(String value) throws IOException {
        onNonSymbolMatched(value, yychar);
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset,
        boolean ignoreKwd)
            throws IOException {
        if (ignoreKwd) {
            if (value.length() > 1) {
                return onFilteredSymbolMatched(value, yychar, null, false);
            } else {
                onNonSymbolMatched(value, yychar);
                return false;
            }
        } else {
            return onFilteredSymbolMatched(value, yychar, Consts.kwd, false);
        }
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

    protected boolean takeAllContent() {
        return true;
    }

    protected boolean returnOnSymbol() {
        return false;
    }
%}

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include AdaProductions.lexh
