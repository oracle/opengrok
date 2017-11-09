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
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Scala file
 */

package org.opensolaris.opengrok.analysis.scala;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class ScalaXref
%extends JFlexXref
%unicode
%ignorecase
%int
%include CommonXref.lexh
%{
  /* Must match {WhiteSpace} regex */
  private final static String WHITE_SPACE = "[ \\t\\f]+";

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

Identifier = [a-zA-Z_] [a-zA-Z0-9_]+

File = [a-zA-Z]{FNameChar}* "." ("scala"|"properties"|"props"|"xml"|"conf"|"txt"|"htm"|"html"|"ini"|"jnlp"|"jad"|"diff"|"patch")

Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[0-9]+)(([eE][+-]?[0-9]+)?[ufdlUFDL]*)?

JavadocWithClassArg = "@throws" | "@exception"
JavadocWithParamNameArg = "@param"

ClassName = ({Identifier} ".")* {Identifier}
ParamName = {Identifier} | "<" {Identifier} ">"

%state  STRING COMMENT SCOMMENT QSTRING JAVADOC

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
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

/*{Hier}
        { out.write(Util.breadcrumbPath(urlPrefix+"defs=",yytext(),'.'));}
*/
{Number}        { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

 \"     { yybegin(STRING);out.write("<span class=\"s\">\"");}
 \'     { yybegin(QSTRING);out.write("<span class=\"s\">\'");}
 "/**" / [^/] { yybegin(JAVADOC);out.write("<span class=\"c\">/**");}
 "/*"   { yybegin(COMMENT);out.write("<span class=\"c\">/*");}
 "//"   { yybegin(SCOMMENT);out.write("<span class=\"c\">//");}
}

<STRING> {
 \" {WhiteSpace} \"  { out.write(yytext());}
 \"     { yybegin(YYINITIAL); out.write("\"</span>"); }
 \\\\   { out.write("\\\\"); }
 \\\"   { out.write("\\\""); }
}

<QSTRING> {
 "\\\\" { out.write("\\\\"); }
 "\\\'" { out.write("\\\'"); }
 \' {WhiteSpace} \' { out.write(yytext()); }
 \'     { yybegin(YYINITIAL); out.write("'</span>"); }
}

<COMMENT, JAVADOC> {
"*/"    { yybegin(YYINITIAL); out.write("*/</span>"); }
}

<JAVADOC> {
  {JavadocWithParamNameArg} {WhiteSpace} {ParamName} |
  {JavadocWithClassArg} {WhiteSpace} {ClassName} {
    String text = yytext();
    String[] tokens = text.split(WHITE_SPACE, 2);
    out.append("<strong>").append(tokens[0]).append("</strong>")
      .append(text.substring(tokens[0].length(),
                             text.length() - tokens[1].length()))
      .append("<em>").append(tokens[1]).append("</em>");
  }
  "@" {Identifier} {
    out.append("<strong>").append(yytext()).append("</strong>");
  }
}

<SCOMMENT> {
  {WhspChar}*{EOL} {
    yybegin(YYINITIAL); out.write("</span>");
    startNewLine();
  }
}


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING, JAVADOC> {
"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
{WhspChar}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, SCOMMENT, STRING, QSTRING, JAVADOC> {
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
