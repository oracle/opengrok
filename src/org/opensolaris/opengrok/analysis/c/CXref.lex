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
 */

/*
 * Cross reference a C file
 */

package org.opensolaris.opengrok.analysis.c;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class CXref
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

WhiteSpace     = [ \t\f]+
EOL = \r|\n|\r\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = [a-zA-Z]{FNameChar}* "." ([chts]|"conf"|"java"|"cpp"|"hpp"|"CC"|"txt"|"htm"|"html"|"pl"|"xml"|"cc"|"cxx"|"c++"|"hh"|"hxx"|"h++"|"diff"|"patch")
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[1-9][0-9]*)(([eE][+-]?[0-9]+)?[ufdlUFDL]*)?

%state  STRING COMMENT SCOMMENT QSTRING

%%
<YYINITIAL>{

 \{     { incScope(); writeUnicodeChar(yycharat(0)); }
 \}     { decScope(); writeUnicodeChar(yycharat(0)); }
 \;     { endScope(); writeUnicodeChar(yycharat(0)); }

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline);
}

"#" {WhiteSpace}* "include" {WhiteSpace}* "<" ({File}|{Path}|{Identifier}) ">" {
        Matcher match = Pattern.compile("(#.*)(include)(.*)<(.*)>").matcher(yytext());
        if (match.matches()) {
            out.write(match.group(1));
            writeSymbol(match.group(2), Consts.kwd, yyline);
            out.write(match.group(3));
            out.write("&lt;");
            String path = match.group(4);
            out.write(Util.breadcrumbPath(urlPrefix + "path=", path));
            out.write("&gt;");
        }
}

/*{Hier}
        { out.write(Util.breadcrumbPath(urlPrefix+"defs=",yytext(),'.'));}
*/
{Number} { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

 \\\" | \\\' { out.write(yytext()); }
 \"     { out.write("<span class=\"s\">\""); yypush(STRING, "</span>"); }
 \'     { out.write("<span class=\"s\">\'"); yypush(QSTRING, "</span>"); }
 "/*"   { out.write("<span class=\"c\">/*"); yypush(COMMENT, "</span>"); }
 "//"   { out.write("<span class=\"c\">//"); yypush(SCOMMENT, "</span>"); }
}

<STRING> {
 \" {WhiteSpace} \"  { out.write(yytext()); }
 \"     { out.write(yytext()); yypop(); }
 \\\\   { out.write("\\\\"); }
 \\\"   { out.write("\\\""); }
}

<QSTRING> {
 "\\\\" { out.write("\\\\"); }
 "\\'" { out.write("\\\'"); }
 \' {WhiteSpace} \' { out.write(yytext()); }
 \'     { out.write(yytext()); yypop(); }
}

<COMMENT> {
"*/"    { out.write(yytext()); yypop(); }
}

<SCOMMENT> {
{WhiteSpace}*{EOL}      {
    yypop();
    startNewLine();}
}


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
{WhiteSpace}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, SCOMMENT, STRING, QSTRING> {
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
