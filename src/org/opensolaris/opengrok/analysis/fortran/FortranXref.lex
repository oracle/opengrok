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
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Fortran file
 */
package org.opensolaris.opengrok.analysis.fortran;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class FortranXref
%extends JFlexXrefSimple
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

File = [a-zA-Z]{FNameChar}* ".inc"

%state  STRING SCOMMENT QSTRING LCOMMENT

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Fortran.lexh
%%
<YYINITIAL>{
 ^{Label} {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }
 ^[^ \t\f\r\n]+ {
    pushSpan(LCOMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(htmlize(yytext()));
}

{Identifier} {
    String id = yytext();
    // For historical reasons, FortranXref doesn't link identifiers of length=1
    if (id.length() > 1) {
        writeSymbol(id, Consts.kwd, yyline, false);
    } else {
        out.write(id);
    }
}

"<" ({File}|{FPath}) ">" {
    out.write("&lt;");
    String file = yytext();
    file = file.substring(1, file.length() - 1);
    out.write("<a href=\""+urlPrefix+"path=");
    out.write(file);out.write("\">");
    out.write(file);out.write("</a>");
    out.write("&gt;");
}

/*{Hier}
        { out.write(Util.breadcrumbPath(urlPrefix+"defs=",yytext(),'.'));}
*/
{Number}        {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }

 \"     {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \'     {
    pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \!     {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(htmlize(yytext()));
 }
}

<STRING> {
 \"\"    { out.write(htmlize(yytext()));}
 \"     {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<QSTRING> {
 \'\'    { out.write(htmlize(yytext())); }
 \'     {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<STRING, QSTRING> {
    {WhspChar}*{EOL}    {
        disjointSpan(null);
        startNewLine();
        disjointSpan(HtmlConsts.STRING_CLASS);
    }
}

<SCOMMENT, LCOMMENT> {
    {WhspChar}*{EOL}    {
        yypop();
        startNewLine();
    }
}

<YYINITIAL, STRING, SCOMMENT, QSTRING, LCOMMENT> {
[&<>\'\"]    { out.write(htmlize(yytext())); }
{WhspChar}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<SCOMMENT, STRING, QSTRING> {
{FPath}
        { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}

{File}
        {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);out.write("\">");
        out.write(path);out.write("</a>");}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}

<SCOMMENT, STRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, FortranUtils.CHARLITERAL_APOS_DELIMITER);
    }
}
