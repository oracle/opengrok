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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Golang file
 */

package org.opensolaris.opengrok.analysis.golang;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

/**
 * @author Patrick Lundquist
 */
%%
%public
%class GolangXref
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

File = [a-zA-Z]{FNameChar}* "." ([Gg][Oo] | [Tt][Xx][Tt] | [Hh][Tt][Mm][Ll]? |
    [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state STRING COMMENT SCOMMENT QSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Golang.lexh
%%
<YYINITIAL> {
    {Identifier} {
        String id = yytext();
        writeSymbol(id, Consts.kwd, yyline);
    }
    {Number}     {
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        out.write(yytext());
        disjointSpan(null);
    }
    \"           {
        pushSpan(STRING, HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
    }
    \'           {
        pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
    }
    "/*"         {
        pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
        out.write(yytext());
    }
    "//"         {
        pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
        out.write(yytext());
    }
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

<STRING> {
    \\[\"\\] |
    \" {WhiteSpace} \"    { out.write(htmlize(yytext())); }
    \"    {
        out.write(htmlize(yytext()));
        yypop();
    }
}

<QSTRING> {
    \\[\'\\] |
    \' {WhiteSpace} \'    { out.write(htmlize(yytext())); }
    \'    {
        out.write(htmlize(yytext()));
        yypop();
    }
}

<COMMENT> {
    "*/"    {
        out.write(yytext());
        yypop();
    }
}

<SCOMMENT> {
    {WhspChar}*{EOL}    {
        yypop();
        startNewLine();
    }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
    [&<>\'\"]          { out.write(htmlize(yytext())); }
    {WhspChar}*{EOL}   { startNewLine();                }
    {WhiteSpace}       { out.write(yytext());           }
    [!-~]              { out.write(yycharat(0)); }
    [^\n]              { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, SCOMMENT, QSTRING> {
    {FPath} { out.write(Util.breadcrumbPath(urlPrefix + "path=", yytext(), '/')); }
    {File} {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
    }
    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+ { writeEMailAddress(yytext()); }
}

<STRING, SCOMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<COMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.END_C_COMMENT);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.APOS_NO_BSESC);
    }
}
