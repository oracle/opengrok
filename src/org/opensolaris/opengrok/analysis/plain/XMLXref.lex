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
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
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
 "<!--"  { yybegin(COMMENT); out.write("<span class=\"c\">&lt;!--"); }
 "<![CDATA[" {
    yybegin(CDATA);
    out.write("&lt;<span class=\"n\">![CDATA[</span><span class=\"c\">");
 }
 "<"    { yybegin(TAG); out.write("&lt;");}
}

<TAG> {
[a-zA-Z_0-9]+{WhspChar}*\= { out.write("<b>"); out.write(yytext()); out.write("</b>"); }
[a-zA-Z_0-9]+ { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }
\"      { yybegin(STRING); out.write("<span class=\"s\">\""); }
\'      { yybegin(SSTRING); out.write("<span class=\"s\">'"); }
">"      { yybegin(YYINITIAL); out.write("&gt;"); }
"<"      { yybegin(YYINITIAL); out.write("&lt;"); }
}

<STRING> {
 \" {WhspChar}* \"  { out.write(yytext());}
 \"     { yybegin(TAG); out.write("\"</span>"); }
}

<STRING, SSTRING, COMMENT, CDATA> {
 "<"    {out.write( "&lt;");}
 ">"    {out.write( "&gt;");}
}

<SSTRING> {
 \' {WhspChar}* \'  { out.write(yytext());}
 \'     { yybegin(TAG); out.write("'</span>"); }
}

<COMMENT> {
"-->"     { yybegin(YYINITIAL); out.write("--&gt;</span>"); }
}

<CDATA> {
  "]]>" {
    yybegin(YYINITIAL); out.write("<span class=\"n\">]]</span></span>&gt;");
  }
}

<YYINITIAL, COMMENT, CDATA, STRING, SSTRING, TAG> {
{File}|{AlmostAnyFPath}
  {
    final String path = yytext();
    final boolean isJavaClass=StringUtils.isPossiblyJavaClass(path);
    final char separator = isJavaClass ? '.' : '/';
    final String hyperlink =
            Util.breadcrumbPath(urlPrefix + "path=", path, separator,
                                getProjectPostfix(true), isJavaClass);
    out.append(hyperlink);
  }

{BrowseableURI}    {
          appendLink(yytext(), true);
        }

{NameChar}+ "@" {NameChar}+ "." {NameChar}+
        {
          writeEMailAddress(yytext());
        }

"&"     {out.write( "&amp;");}
{WhiteSpace}{EOL} |
    {EOL}   {startNewLine(); }
[!-~] | {WhspChar}    {out.write(yycharat(0));}
[^\n]       { writeUnicodeChar(yycharat(0)); }
}
