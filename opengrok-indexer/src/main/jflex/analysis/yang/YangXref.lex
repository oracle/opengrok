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
 * Cross references a YANG file.
 */

package org.opengrok.indexer.analysis.yang;

import java.io.IOException;
import java.util.Set;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class YangXref
%extends YangLexer
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
    public boolean offerSymbol(String value, int captureOffset, boolean ignoreKwd)
            throws IOException {
        Set<String> keywords = ignoreKwd ? null : Consts.KEYWORDS;
        return onFilteredSymbolMatched(value, yychar + captureOffset, keywords);
    }

    @Override
    public void skipSymbol() {
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
    protected boolean takeAllContent() {
        return true;
    }

    @Override
    protected boolean returnOnSymbol() {
        return false;
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
