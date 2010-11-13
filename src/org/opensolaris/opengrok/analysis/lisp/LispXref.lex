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
 * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Cross reference a Lisp file
 */

package org.opensolaris.opengrok.analysis.lisp;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class LispXref
%extends JFlexXref
%unicode
%ignorecase
%int
%{
  private int nestedComment;

  @Override
  public void reInit(char[] buf, int len) {
        super.reInit(buf, len);
        nestedComment = 0;
  }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

WhiteSpace     = [ \t\f]+
EOL = \r|\n|\r\n
Identifier = [\-\+\*\!\@\$\%\&\/\?\.\,\:\{\}\=a-zA-Z0-9_\<\>]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = [a-zA-Z] {FNameChar}+ "." ([a-zA-Z]+)
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

Number = ([0-9]+\.[0-9]+|[0-9][0-9]*|"#" [boxBOX] [0-9a-fA-F]+)

%state  STRING COMMENT SCOMMENT

%%
<YYINITIAL>{

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline);
}

{Number}        { out.write("<span class=\"n\">");
                  out.write(yytext());
                  out.write("</span>"); }

 \"     { yybegin(STRING);out.write("<span class=\"s\">\"");}
 ";"    { yybegin(SCOMMENT);out.write("<span class=\"c\">;");}
}

<STRING> {
 \" {WhiteSpace} \"  { out.write(yytext()); }
 \"     { yybegin(YYINITIAL); out.write("\"</span>"); }
 \\\\   { out.write("\\\\"); }
 \\\"   { out.write("\\\""); }
}

<YYINITIAL, COMMENT> {
 "#|"   { yybegin(COMMENT);
          if (nestedComment++ == 0) { out.write("<span class=\"c\">"); }
          out.write("#|");
        }
 }

<COMMENT> {
 "|#"   { out.write("|#");
          if (--nestedComment == 0) {
            yybegin(YYINITIAL);
            out.write("</span>");
          }
        }
}

<SCOMMENT> {
  {WhiteSpace}*{EOL} {
    yybegin(YYINITIAL); out.write("</span>");
    startNewLine();
  }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT> {
"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
{WhiteSpace}*{EOL} { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 .      { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, SCOMMENT> {
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
         String url = yytext();
         out.write("<a href=\"");
         out.write(url);out.write("\">");
         out.write(url);out.write("</a>");}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}
