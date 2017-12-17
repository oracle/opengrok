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

package org.opensolaris.opengrok.analysis.eiffel;

import java.io.IOException;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class EiffelSymbolTokenizer
%extends JFlexTokenizer
%implements EiffelLexer
%init{
    super(in);
    h = new EiffelLexHelper(VSTRING, this);
%init}
%unicode
%ignorecase
%int
%char
%include CommonTokenizer.lexh
%{
    private final EiffelLexHelper h;

    private String lastSymbol;

    /**
     * Resets the Eiffel tracked state after {@link #reset()}.
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        h.reset();
        lastSymbol = null;
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

/*
 * SCOMMENT : single-line comment
 * STRING : basic manifest string (literal)
 * VSTRING : verbatim manifest string (literal)
 */
%state SCOMMENT STRING VSTRING

%include Common.lexh
%include CommonURI.lexh
%include EiffelProductions.lexh
