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
 * Cross references a HCL file.
 */

package org.opengrok.indexer.analysis.hcl;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class HCLXref
%extends HCLLexer
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
        return onFilteredSymbolMatched(value, yychar, keywords);
    }

    /** Does nothing. */
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

    /** Gets the value {@code true}. */
    protected boolean takeAllContent() {
        return true;
    }

    /** Gets the value {@code false}. */
    protected boolean returnOnSymbol() {
        return false;
    }

    /**
     * Gets the constant value created by JFlex to represent COMMENT.
     */
    @Override
    public int COMMENT() {
        return COMMENT;
    }

    /**
     * Gets the constant value created by JFlex to represent SCOMMENT.
     */
    @Override
    public int SCOMMENT() {
        return SCOMMENT;
    }

    /**
     * Gets the constant value created by JFlex to represent HERE.
     */
    @Override
    public int HERE() {
        return HERE;
    }

    /**
     * Gets the constant value created by JFlex to represent HEREin.
     */
    @Override
    public int HEREin() {
        return HEREin;
    }
%}

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include HCL.lexh

%%
%include HCLProductions.lexh
