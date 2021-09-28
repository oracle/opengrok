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
 * Cross reference an Erlang file
 */

package org.opengrok.indexer.analysis.erlang;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class ErlangXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
    @Override
    public void yypop() throws IOException {
        onDisjointSpanChanged(null, yychar);
        super.yypop();
    }

    protected void chkLOC() {
        switch (yystate()) {
            case COMMENT:
                break;
            default:
                phLOC();
                break;
        }
    }
%}

IncludeDirective = (include|include_lib)

File = [a-zA-Z]{FNameChar}* "." ([Ee][Rr][Ll] | [Hh][Rr][Ll] | [Aa][Pp][Pp] |
    [Aa][Ss][Nn] | [Yy][Rr][Ll] | [Aa][Ss][Nn][1] | [Xx][Mm][Ll] |
    [Hh][Tt][Mm][Ll]?)

%state  STRING COMMENT QATOM

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Erlang.lexh
%%
<YYINITIAL>{

"?" {Identifier} {  // Macros
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.MACRO_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
}

{Identifier} {
    chkLOC();
    String id = yytext();
    if (!id.equals("_")) {
        onFilteredSymbolMatched(id, yychar, Consts.kwd);
    } else {
        onNonSymbolMatched(id, yychar);
    }
}

"-" {IncludeDirective} "(" ({File}|{FPath}) ")." {
        chkLOC();
        String capture = yytext();
        String parenth = capture.substring(capture.indexOf("("));
        String opening = capture.substring(0, yylength() - parenth.length());
        String lparen = parenth.substring(0, 1);
        int rpos = parenth.indexOf(")");
        String rparen = parenth.substring(rpos);
        String path = parenth.substring(lparen.length(), rpos);

        onNonSymbolMatched(opening.substring(0, 1), yychar);
        onSymbolMatched(opening.substring(1), yychar + 1);
        onNonSymbolMatched(lparen, yychar + opening.length());
        onFilelikeMatched(path, yychar + opening.length() + lparen.length());
        onNonSymbolMatched(rparen, yychar + opening.length() +
            lparen.length() + path.length());
}

^"-" {Identifier} {
    chkLOC();
    String capture = yytext();
    String punc = capture.substring(0, 1);
    String id = capture.substring(1);
    onNonSymbolMatched(punc, yychar);
    onFilteredSymbolMatched(id, yychar + punc.length(), Consts.modules_kwd);
}

{ErlInt} |
    {Number}    {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar + yylength());
}

 \"     {
    chkLOC();
    yypush(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }

 \'     {
    chkLOC();
    yypush(QATOM);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }

 "%"    {
    yypush(COMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \\[\"\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<QATOM> {
 \\[\'\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \'    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<COMMENT> {
  {ErlangWhspChar}*{EOL} {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
}

<YYINITIAL, STRING, COMMENT, QATOM> {
{ErlangWhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 \s     { onNonSymbolMatched(yytext(), yychar); }
 [^]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING, COMMENT, QATOM> {
 {FPath}    {
     chkLOC();
     onPathlikeMatched(yytext(), '/', false, yychar);
 }

{File}
        {
        chkLOC();
        String path = yytext();
        onFilelikeMatched(path, yychar);
 }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
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

<QATOM> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar, StringUtils.APOS_NO_BSESC);
    }
}
