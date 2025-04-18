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
 * Copyright (c) 2025, Yelisey Romanov <progoramur@gmail.com>.
 */

/*
 * Cross reference a OCaml file
 */

package org.opengrok.indexer.analysis.ocaml;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.web.HtmlConsts;

/**
 * @author Yelisey Romanov
 */
%%
%public
%class OCamlXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
    private int nestedComment;
    private String quotedStringKey;

    @Override
    public void reset() {
        super.reset();
        nestedComment = 0;
        quotedStringKey = "";
    }

    @Override
    public void yypop() throws IOException {
        onDisjointSpanChanged(null, yychar);
        super.yypop();
    }

    protected void chkLOC() {
        switch (yystate()) {
            case BCOMMENT:
                break;
            default:
                phLOC();
                break;
        }
    }
%}

%state STRING QSTRING QEXTENSIONBEGIN BCOMMENT

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include OCaml.lexh
%%
<YYINITIAL> {
    {Character}  {
        chkLOC();
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
    }
    {Identifier} {
        chkLOC();
        String id = yytext();
        onFilteredSymbolMatched(id, yychar, Consts.kwd);
    }
    {Extension}     {
        chkLOC();
        onDisjointSpanChanged(HtmlConsts.MACRO_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
    }
    {Number}     {
        chkLOC();
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
    }
}

<STRING> {
    \\[\"\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
    \"          {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypop();
        if (nestedComment > 0) {
            onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        }
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

<QEXTENSIONBEGIN> {
    {QuotedExtensionKey}         {
        chkLOC();
        yypop();
        yypush(QSTRING);
        if (nestedComment > 0) {
            onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        } else {
            onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        }
        onNonSymbolMatched(yytext(), yychar);

        String key = yytext();
        quotedStringKey = key.substring(0, key.length() - 1);
    }
}

<QSTRING> {
    {QuotedStringEnd}        {
        String key = yytext();
        if (quotedStringKey.equals(
              key.substring(1, key.length() - 1))) {
            quotedStringKey = "";
            chkLOC();
            onNonSymbolMatched(yytext(), yychar);
            yypop();
            if (nestedComment > 0) {
                onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
            }
        } else {
            chkLOC();
            onNonSymbolMatched(yytext(), yychar);
        }
    }
    /*
     * "A string may include a 'gap'-—two backslants enclosing white
     * characters—-which is ignored. This allows one to write long strings on
     * more than one line by writing a backslant at the end of one line and at
     * the start of the next." N.b. OpenGrok does not explicitly recognize the
     * "gap" but since a STRING must end in a non-escaped quotation mark, just
     * allow STRINGs to be multi-line regardless of syntax.
     */
}

<YYINITIAL, BCOMMENT> {
    "(*"    {
        if (nestedComment++ == 0) {
            yypush(BCOMMENT);
            onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        }
        onNonSymbolMatched(yytext(), yychar);
    }
    \"           {
        chkLOC();
        yypush(STRING);
        if (nestedComment == 0) {
            onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        }
        onNonSymbolMatched(yytext(), yychar);
    }
    {QuotedStringBegin}         {
        chkLOC();
        yypush(QSTRING);
        if (nestedComment == 0) {
            onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        }
        onNonSymbolMatched(yytext(), yychar);

        String key = yytext();
        quotedStringKey = key.substring(1, key.length() - 1);
    }
    {QuotedExtensionBegin}     {
        chkLOC();
        if (nestedComment == 0) {
            onDisjointSpanChanged(HtmlConsts.MACRO_CLASS, yychar);
        }
        onNonSymbolMatched(yytext(), yychar);
        yypush(QEXTENSIONBEGIN);
    }
}

<BCOMMENT> {
    "*)"    {
        onNonSymbolMatched(yytext(), yychar);
        if (--nestedComment == 0) {
            yypop();
        }
    }
}

{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
[[\s]--[\n]]        { onNonSymbolMatched(yytext(), yychar); }
[^\n]               { chkLOC(); onNonSymbolMatched(yytext(), yychar); }

<STRING, BCOMMENT> {
    {FPath}    {
        chkLOC();
        onPathlikeMatched(yytext(), '/', false, yychar);
    }
    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+    {
        chkLOC();
        onEmailAddressMatched(yytext(), yychar);
    }
}

<STRING> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar);
    }
}

<BCOMMENT> {
    {BrowseableURI} \}?    {
        onUriMatched(yytext(), yychar);
    }
}
