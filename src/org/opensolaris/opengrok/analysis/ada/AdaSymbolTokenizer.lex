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
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets Ada symbols - ignores comments, strings, keywords
 */

package org.opensolaris.opengrok.analysis.ada;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class AdaSymbolTokenizer
%extends JFlexTokenizer
%implements AdaLexer
%unicode
%ignorecase
%int
%char
%init{
    super(in);
    h = new AdaLexHelper(this);
%init}
%include CommonTokenizer.lexh
%{
    private final AdaLexHelper h;

    private String lastSymbol;

    /**
     * Reinitialize the tokenizer with new reader.
     * @throws java.io.IOException in case of I/O error
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        h.reset();
    }

    @Override
    public void offer(String value) throws IOException {
        // noop
    }

    @Override
    public void offerNonword(String value) throws IOException {
        // noop
    }

    public void takeUnicode(String value) throws IOException {
        // noop
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset,
        boolean ignoreKwd)
            throws IOException {
        if (ignoreKwd || !Consts.kwd.contains(value.toLowerCase())) {
            lastSymbol = value;
            setAttribs(value, yychar + captureOffset, yychar + captureOffset +
                value.length());
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

    protected boolean takeAllContent() {
        return false;
    }

    protected boolean returnOnSymbol() {
        return lastSymbol != null;
    }

    protected String getUrlPrefix() { return null; }

    protected void appendProject() { /* noop */ }

    protected void appendLink(String s, boolean b) { /* noop */ }

    protected void writeEMailAddress(String s) { /* noop */ }
%}

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include AdaProductions.lexh
