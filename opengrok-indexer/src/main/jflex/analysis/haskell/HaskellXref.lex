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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Haskell file
 */

package org.opengrok.indexer.analysis.haskell;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.web.HtmlConsts;

/**
 * @author Harry Pan
 */
%%
%public
%class HaskellXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
    private int nestedComment;

    @Override
    public void reset() {
        super.reset();
        nestedComment = 0;
    }

    @Override
    public void yypop() throws IOException {
        onDisjointSpanChanged(null, yychar);
        super.yypop();
    }

    protected void chkLOC() {
        switch (yystate()) {
            case COMMENT:
            case BCOMMENT:
                break;
            default:
                phLOC();
                break;
        }
    }
%}

%state STRING CHAR COMMENT BCOMMENT

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Haskell.lexh
%%
<YYINITIAL> {
    {Identifier} {
        chkLOC();
        String id = yytext();
        onFilteredSymbolMatched(id, yychar, Consts.kwd);
    }
    {Number}     {
        chkLOC();
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
    }
    \"           {
        chkLOC();
        yypush(STRING);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
    \'           {
        chkLOC();
        yypush(CHAR);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
    "--"         {
        yypush(COMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }

    {NotComments}    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING> {
    \\[\"\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
    \"          {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypop();
    }
    /*
     * "A string may include a 'gap'-—two backslants enclosing white
     * characters—-which is ignored. This allows one to write long strings on
     * more than one line by writing a backslant at the end of one line and at
     * the start of the next." N.b. OpenGrok does not explicltly recognize the
     * "gap" but since a STRING must end in a non-escaped quotation mark, just
     * allow STRINGs to be multi-line regardless of syntax.
     */
}

<CHAR> {    // we don't need to consider the case where prime is part of an identifier since it is handled above
    \\[\'\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
    \'          {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypop();
    }
    /*
     * N.b. though only a single char is valid Haskell syntax, OpenGrok just
     * waits to end CHAR at a non-escaped apostrophe regardless of count.
     */
}

<COMMENT> {
    {WhspChar}*{EOL}    {
        yypop();
        onEndOfLineMatched(yytext(), yychar);
    }
}

<YYINITIAL, BCOMMENT> {
    "{-"    {
        if (nestedComment++ == 0) {
            yypush(BCOMMENT);
            onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        }
        onNonSymbolMatched(yytext(), yychar);
    }
}

<BCOMMENT> {
    "-}"    {
        onNonSymbolMatched(yytext(), yychar);
        if (--nestedComment == 0) {
            yypop();
        }
    }
}

{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
[[\s]--[\n]]        { onNonSymbolMatched(yytext(), yychar); }
[^\n]               { chkLOC(); onNonSymbolMatched(yytext(), yychar); }

<STRING, COMMENT, BCOMMENT> {
    {FPath}    {
        chkLOC();
        onPathlikeMatched(yytext(), '/', false, yychar);
    }
    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+    {
        chkLOC();
        onEmailAddressMatched(yytext(), yychar);
    }
}

<STRING, COMMENT> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar);
    }
}

<BCOMMENT> {
    /*
     * Right curly bracket is not a valid URI character, so it won't be in a
     * {BrowseableURI} capture, but a hyphen is valid. Thus a nested comment
     * ending token, -}, can hide at the end of a URI. Work around this by
     * capturing a possibly-trailing right curly bracket, and match a special,
     * Haskell-specific collateral capture pattern.
     */
    {BrowseableURI} \}?    {
        onUriMatched(yytext(), yychar, HaskellUtils.MAYBE_END_NESTED_COMMENT);
    }
}
