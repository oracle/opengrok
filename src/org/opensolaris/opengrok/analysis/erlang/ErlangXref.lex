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
 */

/*
 * Cross reference an Erlang file
 */

package org.opensolaris.opengrok.analysis.erlang;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class ErlangXref
%extends JFlexXref
%unicode
%ignorecase
%int
%{
  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

WhiteSpace     = [ \t\f\u{B}]+
EOL = \r|\n|\r\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_@]+

IncludeDirective = (include|include_lib)
//PPDirective = (define|undef|ifdef|else|endif)
//Directive = (module|author|compile|export|import)

// ErlChar = \$ASCII
ErlInt = ([12][0-9]|3[0-6]|[1-9])#[0-9]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = [a-zA-Z]{FNameChar}* "." ("erl"|"hrl"|"app"|"asn"|"yrl"|"asn1"|"xml"|"html")
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[0-9]+)(([eE][+-]?[0-9]+)?[loxbLOXBjJ]*)?

%state  STRING COMMENT QATOM

%%
<YYINITIAL>{

"?" {Identifier} {  // Macros
    out.write("<span class=\"xm\">"); out.write(yytext()); out.write("</span>");
}

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline);
}

"-" {IncludeDirective} "(" ({File}|{Path}) ")." {
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

{ErlInt}        { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }
{Number}        { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

 \"     { yybegin(STRING);out.write("<span class=\"s\">\"");}
 \'     { yybegin(QATOM);out.write("<span class=\"s\">\'");}
 "%"   { yybegin(COMMENT);out.write("<span class=\"c\">%");}
}

<STRING> {
 \"     { yybegin(YYINITIAL); out.write("\"</span>"); }
 \\\\   { out.write("\\\\"); }
 \\\"   { out.write("\\\""); }
}

<QATOM> {
 \'     { yybegin(YYINITIAL); out.write("\'</span>"); }
 \\\\   { out.write("\\\\"); }
 \\\'   { out.write("\\\'"); }
}

<COMMENT> {
  {WhiteSpace}*{EOL} {
    yybegin(YYINITIAL); out.write("</span>");
    startNewLine();
  }
}

<YYINITIAL, STRING, COMMENT, QATOM> {
"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
{WhiteSpace}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 .      { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, STRING, QATOM> {
{Path}
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

("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
        {
          appendLink(yytext());
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}
