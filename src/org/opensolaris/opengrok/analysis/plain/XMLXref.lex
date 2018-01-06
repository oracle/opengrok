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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.plain;

import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.analysis.EmphasisHint;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class XMLXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%init{
    yyline = 1;
%init}
%include CommonLexer.lexh

File = {FNameChar}+ "." ([a-zA-Z]+) {FNameChar}*

/*
 * Differs from FPath in that the path segments are only constrained to be
 * {FNameChar} -- except the last character must be {ASCII_ALPHA} or {DIGIT}.
 */
AlmostAnyFPath = "/"? {FNameChar}+ ("/" {FNameChar}+)+[a-zA-Z0-9]

FileChar = [a-zA-Z_0-9_\-\/]
NameChar = {FileChar}|"."

%state TAG STRING COMMENT SSTRING CDATA
%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%%

<YYINITIAL> {
 "<!--"    {
    yybegin(COMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched("<!--", yychar);
 }
 "<![CDATA[" {
    yybegin(CDATA);
    onNonSymbolMatched("<", yychar);
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched("![CDATA[", yychar);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
 }
 "<"    { yybegin(TAG); onNonSymbolMatched("<", yychar); }
}

<TAG> {
 [a-zA-Z_0-9]+{WhspChar}*\=    {
    onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
 }
 [a-zA-Z_0-9]+    {
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
 \"      {
    yybegin(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \'      {
    yybegin(SSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
[><]    { yybegin(YYINITIAL); onNonSymbolMatched(yytext(), yychar); }
}

<STRING> {
 \" {WhspChar}* \"    { onNonSymbolMatched(yytext(), yychar); }
 \"     {
    yybegin(TAG);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
}

<SSTRING> {
 \' {WhspChar}* \'    { onNonSymbolMatched(yytext(), yychar); }
 \'     {
    yybegin(TAG);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
}

<COMMENT> {
 "-->"     {
    yybegin(YYINITIAL);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
}

<CDATA> {
  "]]>" {
    yybegin(YYINITIAL);
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched("]]", yychar);
    onDisjointSpanChanged(null, yychar);
    onNonSymbolMatched(">", yychar);
  }
}

<YYINITIAL, COMMENT, CDATA, STRING, SSTRING, TAG> {

{File}|{AlmostAnyFPath}
  {
    final String path = yytext();
    final boolean isJavaClass=StringUtils.isPossiblyJavaClass(path);
    final char separator = isJavaClass ? '.' : '/';
    onPathlikeMatched(path, separator, isJavaClass, yychar);
  }

{BrowseableURI}    {
          onUriMatched(yytext(), yychar);
        }

{NameChar}+ "@" {NameChar}+ "." {NameChar}+
        {
          onEmailAddressMatched(yytext(), yychar);
        }

{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
[^\n]    { onNonSymbolMatched(yytext(), yychar); }
}
