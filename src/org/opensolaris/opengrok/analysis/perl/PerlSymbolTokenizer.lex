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
 * Gets Perl symbols - ignores comments, strings, keywords
 */

package org.opensolaris.opengrok.analysis.perl;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class PerlSymbolTokenizer
%extends JFlexTokenizer
%implements PerlLexListener
%unicode
%int
%char
%init{
    super(in);
    h = new PerlLexHelper(QUO, QUOxN, QUOxL, QUOxLxN, this,
        HERE, HERExN, HEREin, HEREinxN);
%init}
%include CommonTokenizer.lexh
%{
    private final PerlLexHelper h;

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
    public void take(String value) throws IOException {
        // noop
    }

    @Override
    public void takeNonword(String value) throws IOException {
        // noop
    }

    public void takeUnicode(String value) throws IOException {
        // noop
    }

    @Override
    public boolean takeSymbol(String value, int captureOffset,
        boolean ignoreKwd)
            throws IOException {
        if (ignoreKwd || !Consts.kwd.contains(value)) {
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
    public void takeKeyword(String value) throws IOException {
        lastSymbol = null;
    }

    @Override
    public void startNewLine() throws IOException {
        // noop
    }

    @Override
    public void abortQuote() throws IOException {
        yypop();
        if (h.areModifiersOK()) yypush(QM);
        take(HtmlConsts.ZSPAN);
    }

    // If the state is YYINITIAL, then transitions to INTRA; otherwise does
    // nothing, because other transitions would have saved the state.
    public void maybeIntraState() {
        if (yystate() == YYINITIAL) yybegin(INTRA);
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
%include PerlProductions.lexh
