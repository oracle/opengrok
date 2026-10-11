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

/*
 * Gets YANG symbols -- ignores comments, strings, and keywords.
 */

package org.opengrok.indexer.analysis.yang;

import java.io.IOException;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class YangSymbolTokenizer
%extends YangLexer
%unicode
%int
%char
%include ../CommonLexer.lexh
%{
    private String lastSymbol;

    @Override
    public void reset() {
        super.reset();
        lastSymbol = null;
    }

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
    }

    @Override
    public void disjointSpan(String className) throws IOException {
    }

    @Override
    public void phLOC() {
    }

    @Override
    protected boolean takeAllContent() {
        return false;
    }

    @Override
    protected boolean returnOnSymbol() {
        return lastSymbol != null;
    }

    @Override
    public int COMMENT() {
        return COMMENT;
    }

    @Override
    public int SCOMMENT() {
        return SCOMMENT;
    }
%}

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Yang.lexh

%%
%include YangProductions.lexh
