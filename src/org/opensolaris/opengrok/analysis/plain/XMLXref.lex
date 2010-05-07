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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
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
%line
%{
  public void write(Writer out) throws IOException {
        this.out = out;
        Util.readableLine(1, out, annotation);
        yyline = 2;
        while(yylex() != YYEOF);
  }
  public void reInit(char[] buf, int len) {
        yyreset((Reader) null);
        zzBuffer = buf;
        zzEndRead = len;
        zzAtEOF = true;
        zzStartRead = 0;
        annotation = null;
  }

%}
WhiteSpace     = [ \t\f]
EOL = \r|\n|\r\n|\u2028|\u2029|\u000B|\u000C|\u0085
URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([a-zA-Z]+) {FNameChar}*
Path = "/"? {FNameChar}+ ("/" {FNameChar}+)+[a-zA-Z0-9]

FileChar = [a-zA-Z_0-9_\-\/]
NameChar = {FileChar}|"."

%state TAG STRING COMMENT SSTRING CDATA
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
[a-zA-Z_0-9]+{WhiteSpace}*\= { out.write("<b>"); out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); out.write("</b>"); }
[a-zA-Z_0-9]+ { out.write("<span class=\"n\">"); out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); out.write("</span>"); }
\"      { yybegin(STRING); out.write("<span class=\"s\">\""); }
\'      { yybegin(SSTRING); out.write("<span class=\"s\">'"); }
">"      { yybegin(YYINITIAL); out.write("&gt;"); }
"<"      { yybegin(YYINITIAL); out.write("&lt;"); }
}

<STRING> {
 \" {WhiteSpace}* \"  { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);}
 \"     { yybegin(TAG); out.write("\"</span>"); }
}

<STRING, SSTRING, COMMENT, CDATA> {
 "<"    {out.write( "&lt;");}
 ">"    {out.write( "&gt;");}
}

<SSTRING> {
 \' {WhiteSpace}* \'  { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);}
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
{File}|{Path}
  {
    final String path = yytext();
    final char separator = StringUtils.isPossiblyJavaClass(path) ? '.' : '/';
    final String hyperlink =
            Util.breadcrumbPath(urlPrefix + "path=", path, separator,
                                getProjectPostfix(), true);
    out.append(hyperlink);
  }

("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
        {String s=yytext();
         out.write("<a href=\"");
         out.write(s);out.write("\">");
         out.write(s);out.write("</a>");}

{NameChar}+ "@" {NameChar}+ "." {NameChar}+
        {
                for(int mi = zzStartRead; mi < zzMarkedPos; mi++) {
                        if(zzBuffer[mi] != '@') {
                                out.write(zzBuffer[mi]);
                        } else {
                                out.write(" (a] ");
                        }
                }
/*              String s=yytext();
                out.write("<a href=\"mailto:");
                out.write(s);out.write("\">");
                out.write(s);out.write("</a>");*/
        }

"&"     {out.write( "&amp;");}
{EOL}   {Util.readableLine(yyline, out, annotation); }
[ !-~\t\f]      {out.write(yycharat(0));}
.       { writeUnicodeChar(yycharat(0)); }
}
