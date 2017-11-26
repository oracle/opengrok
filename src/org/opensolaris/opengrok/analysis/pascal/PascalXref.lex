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

import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class PascalXref
%extends JFlexXref
%unicode
%ignorecase
%int
%include CommonLexer.lexh

Identifier = [a-zA-Z_] [a-zA-Z0-9_]+

File = [a-zA-Z]{FNameChar}* "." ("pas"|"properties"|"props"|"xml"|"conf"|"txt"|"htm"|"html"|"ini"|"diff"|"patch")

Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[0-9]+)(([eE][+-]?[0-9]+)?[ufdlUFDL]*)?

%state  STRING COMMENT SCOMMENT QSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%%
<YYINITIAL>{
 "begin"     { incScope(); out.write(yytext()); }
 "end"     { decScope(); out.write(yytext());}
 \;     { endScope(); writeUnicodeChar(yycharat(0)); }

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline);
}

"<" ({File}|{FPath}) ">" {
        out.write(htmlize("<"));
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
        out.write(htmlize(">"));
}

 {Number}        {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }

 \"     {
    yybegin(STRING);
    disjointSpan(HtmlConsts.STRING_CLASS);
    out.write(htmlize("\""));
 }
 \'     {
    yybegin(QSTRING);
    disjointSpan(HtmlConsts.STRING_CLASS);
    out.write(htmlize("\'"));
 }
 \{     {
    yybegin(COMMENT);
    disjointSpan(HtmlConsts.COMMENT_CLASS);
    out.write("{");
 }
 "//"   {
    yybegin(SCOMMENT);
    disjointSpan(HtmlConsts.COMMENT_CLASS);
    out.write("//");
 }
}

<STRING> {
 \" {WhiteSpace} \"    { out.write(htmlize(yytext())); }
 \"     {
    yybegin(YYINITIAL);
    out.write(htmlize("\""));
    disjointSpan(null);
 }
 \\\\   { out.write("\\\\"); }
 \\\"   { out.write(htmlize("\\\"")); }
}

<QSTRING> {
 "\\\\" { out.write("\\\\"); }
 "\\\'"    { out.write(htmlize("\\\'")); }
 \' {WhiteSpace} \'    { out.write(htmlize(yytext())); }
 \'     {
    yybegin(YYINITIAL);
    out.write(htmlize("'"));
    disjointSpan(null);
 }
}

<COMMENT> {
 \}      {
    yybegin(YYINITIAL);
    out.write("}");
    disjointSpan(null);
 }
}


<SCOMMENT> {
  {WhspChar}*{EOL} {
    yybegin(YYINITIAL);
    disjointSpan(null);
    startNewLine();
  }
}


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
[&<>\'\"]    { out.write(htmlize(yytext())); }
{WhspChar}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, SCOMMENT, STRING, QSTRING> {
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

{BrowseableURI}    {
          appendLink(yytext(), true);
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}
