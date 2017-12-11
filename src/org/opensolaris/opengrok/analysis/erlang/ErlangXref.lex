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
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference an Erlang file
 */

package org.opensolaris.opengrok.analysis.erlang;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class ErlangXref
%extends JFlexXrefSimple
%unicode
%int
%include CommonXref.lexh
%{
  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

IncludeDirective = (include|include_lib)

File = [a-zA-Z]{FNameChar}* "." ([Ee][Rr][Ll] | [Hh][Rr][Ll] | [Aa][Pp][Pp] |
    [Aa][Ss][Nn] | [Yy][Rr][Ll] | [Aa][Ss][Nn][1] | [Xx][Mm][Ll] |
    [Hh][Tt][Mm][Ll]?)

%state  STRING COMMENT QATOM

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Erlang.lexh
%%
<YYINITIAL>{

"?" {Identifier} {  // Macros
    disjointSpan(HtmlConsts.MACRO_CLASS);
    out.write(yytext());
    disjointSpan(null);
}

{Identifier} {
    String id = yytext();
    if (!id.equals("_")) {
        writeSymbol(id, Consts.kwd, yyline);
    } else {
        out.write(id);
    }
}

"-" {IncludeDirective} "(" ({File}|{FPath}) ")." {
        out.write("&lt;");
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
        out.write("&gt;");
}

^"-" {Identifier} {
    String capture = yytext();
    String punc = capture.substring(0, 1);
    String id = capture.substring(1);
    out.write(punc);
    writeSymbol(id, Consts.modules_kwd, yyline);
}

{ErlInt} |
    {Number}    {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
}

 \"     {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }

 \'     {
    pushSpan(QATOM, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }

 "%"    {
    pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
 }
}

<STRING> {
 \\[\"\\]    { out.write(htmlize(yytext())); }
 \"    {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<QATOM> {
 \\[\'\\]    { out.write(htmlize(yytext())); }
 \'    {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<COMMENT> {
  {ErlangWhspChar}*{EOL} {
    yypop();
    startNewLine();
  }
}

<YYINITIAL, STRING, COMMENT, QATOM> {
[&<>\'\"]    { out.write(htmlize(yytext())); }
{ErlangWhspChar}*{EOL}      { startNewLine(); }
 {ErlangWhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }

 [^]    { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, QATOM> {
{FPath}
        { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}

{File}
        {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}

<STRING, COMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<QATOM> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.APOS_NO_BSESC);
    }
}
