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

package org.opengrok.indexer.analysis.eiffel;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class EiffelXref
%extends JFlexSymbolMatcher
%implements EiffelLexer
%char
%init{
    h = new EiffelLexHelper(VSTRING, SCOMMENT, this);
    yyline = 1;
%init}
%unicode
%ignorecase
%int
%include CommonLexer.lexh
%include CommonXref.lexh
%{
    private final EiffelLexHelper h;

    /**
     * Resets the Eiffel tracked state; {@inheritDoc}
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
            return onFilteredSymbolMatched(value, yychar, null, false);
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

/*
 * SCOMMENT : single-line comment
 * STRING : basic manifest string (literal)
 * VSTRING : verbatim manifest string (literal)
 */
%state SCOMMENT STRING VSTRING

%include Common.lexh
%include CommonURI.lexh
%include EiffelProductions.lexh
