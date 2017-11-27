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
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;

%%
%public
%class PlainXref
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
%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include CommonLaxFPath.lexh
%%
{File} | {RelaxedMiddleFPath}
        {String s=yytext();
        out.write("<a href=\"");out.write(urlPrefix);out.write("path=");
        out.write(s);appendProject();out.write("\">");
        out.write(s);out.write("</a>");}

{BrowseableURI}    {
          appendLink(yytext(), true);
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }

// Bug #13362: If there's a very long sequence that matches {FNameChar}+,
// parsing the file will take forever because of all the backtracking. With
// this rule, we avoid much of the backtracking and speed up the parsing
// (in some cases from hours to seconds!). This rule will not interfere with
// the rules above because JFlex always picks the longest match.
{FNameChar}+ { out.write(yytext()); }

"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
{WhiteSpace}{EOL} |
    {EOL}   {startNewLine(); }
[!-~] | {WhspChar}    {out.write(yycharat(0));}
[^\n]       { writeUnicodeChar(yycharat(0)); }
