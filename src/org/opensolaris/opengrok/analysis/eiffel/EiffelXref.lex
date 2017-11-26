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
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class EiffelXref
%extends JFlexXref
%implements EiffelLexer
%init{
    h = new EiffelLexHelper(VSTRING, this);
%init}
%unicode
%ignorecase
%int
%include CommonXref.lexh
%{
    private final EiffelLexHelper h;

    /**
     * Resets the Eiffel tracked state after {@link #reset()}.
     */
    @Override
    public void reset() {
        super.reset();
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
        if (ignoreKwd) {
            return writeSymbol(value, null, yyline, false);
        } else {
            return writeSymbol(value, Consts.kwd, yyline, false);
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

    protected boolean takeAllContent() {
        return true;
    }

    protected boolean returnOnSymbol() {
        return false;
    }

    protected String getUrlPrefix() { return urlPrefix; }
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
