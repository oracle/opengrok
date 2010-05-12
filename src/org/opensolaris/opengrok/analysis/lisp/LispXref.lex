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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
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
%line
%{
  private int nestedComment;

  public void reInit(char[] buf, int len) {
        yyreset((Reader) null);
        zzBuffer = buf;
        zzEndRead = len;
        zzAtEOF = true;
        zzStartRead = 0;
        nestedComment = 0;
        annotation = null;
  }

  public void write(Writer out) throws IOException {
        this.out = out;
        Util.readableLine(1, out, annotation);
        yyline = 2;
        while(yylex() != YYEOF) {
        }
  }

%}

WhiteSpace     = [ \t\f]+
EOL = \r|\n|\r\n|\u2028|\u2029|\u000B|\u000C|\u0085
Identifier = [\-\+\*\!\@\$\%\&\/\?\.\,\:\{\}\=a-zA-Z0-9_\<\>]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = [a-zA-Z] {FNameChar}+ "." ([a-zA-Z]+)
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

Number = ([0-9][0-9]*|[0-9]+.[0-9]+|"#" [boxBOX] [0-9a-fA-F]+)

%state  STRING COMMENT SCOMMENT

%%
<YYINITIAL>{

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline - 1);
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
    Util.readableLine(yyline, out, annotation);
  }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT> {
"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
{WhiteSpace}*{EOL} { Util.readableLine(yyline, out, annotation); }
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
          out.write(yytext().replace("@", " (at) "));
        }
}
