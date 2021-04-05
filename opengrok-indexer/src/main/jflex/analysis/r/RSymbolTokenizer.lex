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
 * Portions Copyright (c) 2017, 2019-2020, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets R symbols -- ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.r;

import java.io.IOException;
import java.util.regex.Pattern;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class RSymbolTokenizer
%extends RLexer
%unicode
%int
%char
%include ../CommonLexer.lexh
%{
    private String lastSymbol;

    /**
     * Resets the R tracked state; {@inheritDoc}
     */
    @Override
    public void reset() {
        super.reset();
        lastSymbol = null;
    }

    /** Does nothing. */
    @Override
    public void offer(String value) throws IOException {
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset, boolean ignoreKwd)
            throws IOException {
        if (ignoreKwd || !Consts.KEYWORDS.contains(value)) {
            lastSymbol = value;
            onSymbolMatched(value, yychar + captureOffset);
            return true;
        }
        lastSymbol = null;
        return false;
    }

    /** Just forget any last-matched symbol. */
    @Override
    public void skipSymbol() {
        lastSymbol = null;
    }

    /** Just forget any last-matched symbol. */
    @Override
    public void offerKeyword(String value) throws IOException {
        lastSymbol = null;
    }

    /** Does nothing. */
    @Override
    public void startNewLine() throws IOException {
    }

    /** Does nothing. */
    @Override
    public void disjointSpan(String className) throws IOException {
    }

    /** Does nothing. */
    @Override
    public void phLOC() {
    }

    /** Gets the value {@code false}. */
    protected boolean takeAllContent() {
        return false;
    }

    /** Gets a value indicating if a symbol was just matched. */
    protected boolean returnOnSymbol() {
        return lastSymbol != null;
    }

    /**
     * Gets the constant value created by JFlex to represent SCOMMENT.
     */
    @Override
    public int SCOMMENT() {
        return SCOMMENT;
    }
%}

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include R.lexh

%%
%include RProductions.lexh
