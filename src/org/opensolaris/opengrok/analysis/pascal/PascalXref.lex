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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Pascal file
 */

package org.opensolaris.opengrok.analysis.pascal;

import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.analysis.ScopeAction;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class PascalXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%init{
    yyline = 1;
%init}
%include CommonLexer.lexh
%include CommonXref.lexh
%{
    protected void chkLOC() {
        switch (yystate()) {
            case COMMENT:
            case SCOMMENT:
                break;
            default:
                phLOC();
                break;
        }
    }
%}

Identifier = [a-zA-Z_] [a-zA-Z0-9_]+

File = [a-zA-Z]{FNameChar}* "." ("pas"|"properties"|"props"|"xml"|"conf"|"txt"|"htm"|"html"|"ini"|"diff"|"patch")

Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[0-9]+)(([eE][+-]?[0-9]+)?[ufdlUFDL]*)?

%state  STRING COMMENT SCOMMENT QSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%%
<YYINITIAL>{
 "begin"    { chkLOC(); onScopeChanged(ScopeAction.INC, yytext(), yychar); }
 "end"      { chkLOC(); onScopeChanged(ScopeAction.DEC, yytext(), yychar); }
 \;         { chkLOC(); onScopeChanged(ScopeAction.END, yytext(), yychar); }

{Identifier} {
    chkLOC();
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
}

"<" ({File}|{FPath}) ">" {
        chkLOC();
        onNonSymbolMatched("<", yychar);
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        onFilelikeMatched(path, yychar + 1);
        onNonSymbolMatched(">", yychar + 1 + path.length());
}

 {Number}        {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \"     {
    chkLOC();
    yybegin(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched("\"", yychar);
 }
 \'     {
    yybegin(QSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched("\'", yychar);
 }
 \{     {
    yybegin(COMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched("{", yychar);
 }
 "//"   {
    yybegin(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched("//", yychar);
 }
}

<STRING> {
 \" {WhspChar}+ \"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"     {
    chkLOC();
    yybegin(YYINITIAL);
    onNonSymbolMatched("\"", yychar);
    onDisjointSpanChanged(null, yychar);
 }
 \\[\"\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<QSTRING> {
 \\[\'\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \' {WhspChar}+ \'    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \'     {
    chkLOC();
    yybegin(YYINITIAL);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
}

<COMMENT> {
 \}      {
    yybegin(YYINITIAL);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
}


<SCOMMENT> {
  {WhspChar}*{EOL} {
    yybegin(YYINITIAL);
    onDisjointSpanChanged(null, yychar);
    onEndOfLineMatched(yytext(), yychar);
  }
}


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING, COMMENT, SCOMMENT, STRING, QSTRING> {
{FPath}
        { chkLOC(); onPathlikeMatched(yytext(), '/', false, yychar); }

{File}
        {
        chkLOC();
        String path = yytext();
        onFilelikeMatched(path, yychar);
 }

{BrowseableURI}    {
          chkLOC();
          onUriMatched(yytext(), yychar);
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          chkLOC();
          onEmailAddressMatched(yytext(), yychar);
        }
}
