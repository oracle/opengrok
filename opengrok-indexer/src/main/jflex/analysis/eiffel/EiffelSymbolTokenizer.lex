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
 * Copyright (c) 2017-2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.eiffel;

import java.io.IOException;
import java.util.Locale;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class EiffelSymbolTokenizer
%extends EiffelLexer
%unicode
%ignorecase
%int
%char
%include ../CommonLexer.lexh
%{
    private String lastSymbol;

    /**
     * Resets the Eiffel tracked state; {@inheritDoc}
     */
    @Override
    public void reset() {
        super.reset();
        lastSymbol = null;
    }

    @Override
    public void offer(String value) throws IOException {
        // noop
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset,
        boolean ignoreKwd)
            throws IOException {
        if (ignoreKwd || !Consts.kwd.contains(value.toLowerCase(Locale.ROOT))) {
            lastSymbol = value;
            onSymbolMatched(value, yychar + captureOffset);
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
    public void offerKeyword(String value) throws IOException {
        lastSymbol = null;
    }

    @Override
    public void startNewLine() throws IOException {
        // noop
    }

    @Override
    public void disjointSpan(String className) throws IOException {
        // noop
    }

    @Override
    public void phLOC() {
        // noop
    }

    protected boolean takeAllContent() {
        return false;
    }

    protected boolean returnOnSymbol() {
        return lastSymbol != null;
    }

    /**
     * Gets the constant value created by JFlex to represent SCOMMENT.
     */
    @Override
    int SCOMMENT() { return SCOMMENT; }

    /**
     * Gets the constant value created by JFlex to represent VSTRING.
     */
    @Override
    int VSTRING() { return VSTRING; }
%}

/*
 * SCOMMENT : single-line comment
 * STRING : basic manifest string (literal)
 * VSTRING : verbatim manifest string (literal)
 */
%state SCOMMENT STRING VSTRING

%include ../Common.lexh
%include ../CommonURI.lexh
%include EiffelProductions.lexh
