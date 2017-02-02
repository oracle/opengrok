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
 * Cross reference a Golang file
 */

package org.opensolaris.opengrok.analysis.golang;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.web.Util;

/**
 * @author Patrick Lundquist
 */

%%
%public
%class GolangXref
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
Identifier = [a-zA-Z_] [a-zA-Z0-9_']*
FNameChar = [a-zA-Z0-9_\-\.]
URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+
File = [a-zA-Z]{FNameChar}* "." ("go"|"txt"|"htm"|"html"|"diff"|"patch")
Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[0-9][0-9_]*)([eE][+-]?[0-9]+)?

%state STRING COMMENT SCOMMENT QSTRING

%%
<YYINITIAL> {
    {Identifier} {
        String id = yytext();
        writeSymbol(id, Consts.kwd, yyline);
    }
    {Number}     { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }
    \"           { yybegin(STRING); out.write("<span class=\"s\">\"");                         }
    \'           { yybegin(QSTRING); out.write("<span class=\"s\">\'");                        }
    "/*"         { yybegin(COMMENT); out.write("<span class=\"c\">/*");                        }
    "//"         { yybegin(SCOMMENT); out.write("<span class=\"c\">//");                       }
}

"<" ({File}|{Path}) ">" {
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

<STRING> {
    \" {WhiteSpace} \" { out.write(yytext()); }
    \"                 { yybegin(YYINITIAL); out.write("\"</span>"); }
    \\\\               { out.write("\\\\"); }
    \\\"               { out.write("\\\""); }
}

<QSTRING> {
    "\\\\"             { out.write("\\\\");                         }
    "\\'"              { out.write("\\\'");                         }
    \' {WhiteSpace} \' { out.write(yytext());                       }
    \'                 { yybegin(YYINITIAL); out.write("'</span>"); }
}

<COMMENT> {
    "*/"               { yybegin(YYINITIAL); out.write("*/</span>"); }
}

<SCOMMENT> {
    {WhiteSpace}*{EOL} { yybegin(YYINITIAL); out.write("</span>"); startNewLine(); }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
    "&"                { out.write( "&amp;");           }
    "<"                { out.write( "&lt;");            }
    ">"                { out.write( "&gt;");            }
    {WhiteSpace}*{EOL} { startNewLine();                }
    {WhiteSpace}       { out.write(yytext());           }
    [^\n]              { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, SCOMMENT, QSTRING> {
    {Path} { out.write(Util.breadcrumbPath(urlPrefix + "path=", yytext(), '/')); }
    {File} {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
    }
    ("http" | "https" | "ftp") "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/] {
        appendLink(yytext());
    }
    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+ { writeEMailAddress(yytext()); }
}
