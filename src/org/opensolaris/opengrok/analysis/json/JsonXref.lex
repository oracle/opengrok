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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Json file
 */

package org.opensolaris.opengrok.analysis.json;
import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class JsonXref
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

File = [a-zA-Z]{FNameChar}* "." ([Jj][Ss] |
    [Pp][Rr][Oo][Pp][Ee][Rr][Tt][Ii][Ee][Ss] | [Pp][Rr][Oo][Pp][Ss] |
    [Xx][Mm][Ll] | [Cc][Oo][Nn][Ff] | [Tt][Xx][Tt] | [Hh][Tt][Mm] |
    [Hh][Tt][Mm][Ll]? | [Ii][Nn][Ii] | [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state  STRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Json.lexh
%%
<YYINITIAL>{


//TODO add support for identifiers on the left side, restruct the way how we see quoted strings, ctags detect them as identifiers, xref should print them that way too
//improve per http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf
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

/*
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
}

<STRING> {
 \\[\"\\] |
 \" {WhiteSpace} \"  { out.write(htmlize(yytext())); }
 \"     {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<YYINITIAL, STRING> {
[&<>\'\"]    { out.write(htmlize(yytext())); }

{WhspChar}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING> {
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
