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
 * Cross reference a Python file
 */

package org.opensolaris.opengrok.analysis.python;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class PythonXref
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

File = [a-zA-Z]{FNameChar}* "." ([Pp][Yy] | [Pp][Mm] | [Cc][Oo][Nn][Ff] |
    [Tt][Xx][Tt] | [Hh][Tt][Mm][Ll]? | [Xx][Mm][Ll] | [Ii][Nn][Ii] |
    [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state  STRING LSTRING SCOMMENT QSTRING LQSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Python.lexh
%%
<YYINITIAL>{

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline);
}

"<" ({File}|{FPath}) ">" {
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

 {Number}    {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }

 \"     {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \"\"\" {
    pushSpan(LSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \'     {
    pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \'\'\' {
    pushSpan(LQSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 "#"   {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
 }
}

<STRING> {
 \\[\"\\]    { out.write(htmlize(yytext())); }
 \"     {
    out.write(htmlize(yytext()));
    yypop();
 }
 {WhspChar}*{EOL} {
    yypop();
    startNewLine();
  }
}

<QSTRING> {
 \\[\'\\]    { out.write(htmlize(yytext())); }
 \'     {
    out.write(htmlize(yytext()));
    yypop();
 }
 {WhspChar}*{EOL} {
    yypop();
    startNewLine();
  }
}

<LSTRING> {
 \\[\"\\]    { out.write(htmlize(yytext()));}
 \"\"\" {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<LQSTRING> {
 \\[\'\\]    { out.write(htmlize(yytext())); }
 \'\'\'     {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<SCOMMENT> {
  {WhspChar}*{EOL} {
    yypop();
    startNewLine();
  }
}

<YYINITIAL, STRING, SCOMMENT, QSTRING , LSTRING, LQSTRING> {
[&<>\'\"]    { out.write(htmlize(yytext())); }
{WhspChar}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING, SCOMMENT, STRING, QSTRING , LSTRING, LQSTRING> {
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

<SCOMMENT, STRING, LSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.APOS_NO_BSESC);
    }
}

<LQSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, PythonUtils.LONGSTRING_APOS);
    }
}
