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
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Ruby file
 */

package org.opensolaris.opengrok.analysis.ruby;

import java.io.IOException;
import java.io.Reader;
import java.util.Stack;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class RubyXref
%extends JFlexXref
%implements RubyLexer
%unicode
%int
%char
%init{
    h = getNewHelper();
%init}
%include CommonXref.lexh
%{
    protected Stack<RubyLexHelper> helpers;

    private RubyLexHelper h;

    // TODO move this into an include file when bug #16053 is fixed
    @Override
    protected int getLineNumber() { return yyline; }
    @Override
    protected void setLineNumber(int x) { yyline = x; }

    @Override
    public void reset() {
        super.reset();
        if (helpers != null) helpers.clear();
        h.reset();
    }

    @Override
    public void offer(String value) throws IOException {
        out.write(value);
    }

    @Override
    public void offerNonword(String value) throws IOException {
        out.write(htmlize(value));
    }

    public void takeUnicode(String value) throws IOException {
        for (int i = 0; i < value.length(); i++){
            char c = value.charAt(i);
            writeUnicodeChar(c);
        }
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset,
        boolean ignoreKwd)
            throws IOException {
        if (h.nameLength(value) <= 1) {
            out.write(value);
            return false;
        } else if (ignoreKwd) {
            return writeSymbol(value, null, yyline);
        } else {
            return writeSymbol(value, Consts.kwd, yyline);
        }
    }

    @Override
    public void skipSymbol() {
        // noop
    }

    @Override
    public void offerKeyword(String value) throws IOException {
        writeKeyword(value, yyline);
    }

    /**
     * If the state is YYINITIAL, then transitions to INTRA; otherwise does
     * nothing, because other transitions would have saved the state.
     */
    public void maybeIntraState() {
        if (yystate() == YYINITIAL) yybegin(INTRA);
    }

    protected void pushHelper() {
        if (helpers == null) helpers = new Stack<>();
        helpers.push(h);
        h = getNewHelper();
    }

    public void popHelper() {
        h = helpers.pop();
    }

    protected RubyLexHelper getNewHelper() {
        return new RubyLexHelper(QUO, QUOxN, QUOxL, QUOxLxN, this,
            HERE, HERExN, HEREin, HEREinxN);
    }

    protected boolean takeAllContent() {
        return true;
    }

    protected boolean returnOnSymbol() {
        return false;
    }

    protected String getUrlPrefix() { return urlPrefix; }

    protected void skipLink(String s, Pattern p) { /* noop */ }
%}

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include RubyProductions.lexh
