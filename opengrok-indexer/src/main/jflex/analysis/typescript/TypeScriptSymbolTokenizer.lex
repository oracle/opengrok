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
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets TypeScript symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.typescript;

import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
import java.io.IOException;
%%
%public
%class TypeScriptSymbolTokenizer
%extends TypeScriptLexer
%unicode
%buffer 32766
%int
%char
%include ../CommonLexer.lexh
%{
    private String lastSymbol;

    /**
     * Resets the TypeScript tracked state; {@inheritDoc}
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
     * Gets the constant value created by JFlex to represent COMMENT.
     */
    @Override
    protected int COMMENT() { return COMMENT; }

    /**
     * Gets the constant value created by JFlex to represent SCOMMENT.
     */
    @Override
    protected int SCOMMENT() { return SCOMMENT; }
%}

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include ../javascript/ECMAScript.lexh
// TypeScript.lexh comes after ECMAScript so that TypeScript macros supersede.
%include TypeScript.lexh

%%
// TypeScriptProductions.lexh comes first so that its expressions are preferred.
%include TypeScriptProductions.lexh
%include ../javascript/ECMAScriptProductions.lexh
