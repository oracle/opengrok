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

import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class XMLXref
%extends JFlexXref
%unicode
%ignorecase
%int
%include CommonXref.lexh
%{
  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}
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
    disjointSpan(HtmlConsts.COMMENT_CLASS);
    out.write(htmlize("<!--"));
 }
 "<![CDATA[" {
    yybegin(CDATA);
    out.write(htmlize("<"));
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write("![CDATA[");
    disjointSpan(HtmlConsts.COMMENT_CLASS);
 }
 "<"    { yybegin(TAG); out.write(htmlize("<")); }
}

<TAG> {
 [a-zA-Z_0-9]+{WhspChar}*\=    {
    out.append("<strong>").append(yytext()).append("</strong>");
 }
 [a-zA-Z_0-9]+    {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }
 \"      {
    yybegin(STRING);
    disjointSpan(HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \'      {
    yybegin(SSTRING);
    disjointSpan(HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
[><]    { yybegin(YYINITIAL); out.write(htmlize(yytext())); }
}

<STRING> {
 \" {WhspChar}* \"  { out.write(htmlize(yytext())); }
 \"     {
    yybegin(TAG);
    out.write(htmlize(yytext()));
    disjointSpan(null);
 }
}

<SSTRING> {
 \' {WhspChar}* \'  { out.write(htmlize(yytext())); }
 \'     {
    yybegin(TAG);
    out.write(htmlize(yytext()));
    disjointSpan(null);
 }
}

<COMMENT> {
 "-->"     {
    yybegin(YYINITIAL);
    out.write(htmlize(yytext()));
    disjointSpan(null);
 }
}

<CDATA> {
  "]]>" {
    yybegin(YYINITIAL);
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write("]]");
    disjointSpan(null);
    out.write(htmlize(">"));
  }
}

<YYINITIAL, COMMENT, CDATA, STRING, SSTRING, TAG> {
[&<>\'\"]    { out.write(htmlize(yytext())); }

{File}|{AlmostAnyFPath}
  {
    final String path = yytext();
    final boolean isJavaClass=StringUtils.isPossiblyJavaClass(path);
    final char separator = isJavaClass ? '.' : '/';
    out.write(Util.breadcrumbPath(urlPrefix + "path=", path, separator,
        getProjectPostfix(true), isJavaClass));
  }

{BrowseableURI}    {
          appendLink(yytext(), true);
        }

{NameChar}+ "@" {NameChar}+ "." {NameChar}+
        {
          writeEMailAddress(yytext());
        }

{WhiteSpace}{EOL} |
    {EOL}   {startNewLine(); }
[!-~] | {WhspChar}    {out.write(yycharat(0));}
[^\n]       { writeUnicodeChar(yycharat(0)); }
}
