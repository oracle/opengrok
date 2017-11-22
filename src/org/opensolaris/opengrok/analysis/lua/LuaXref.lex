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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Lua file
 */

package org.opensolaris.opengrok.analysis.lua;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

/**
 * @author Evan Kinney
 */
%%
%public
%class LuaXref
%extends JFlexXrefSimple
%unicode
%int
%include CommonXref.lexh
%{
    int bracketLevel;

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }

    @Override
    public void reset() {
        super.reset();
        bracketLevel = 0;
    }
%}

File = [a-zA-Z]{FNameChar}* "." ([Ll][Uu][Aa] | [Tt][Xx][Tt] |
    [Hh][Tt][Mm][Ll]? | [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state STRING LSTRING COMMENT SCOMMENT QSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Lua.lexh
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
    "[" [=]* "["    {
        String capture = yytext();
        bracketLevel = LuaUtils.countOpeningLongBracket(capture);
        pushSpan(LSTRING, HtmlConsts.STRING_CLASS);
        out.write(capture);
    }
    \'           {
        pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
    }
    "--[" [=]* "["    {
        String capture = yytext();
        String bracket = capture.substring(2);
        bracketLevel = LuaUtils.countOpeningLongBracket(bracket);
        pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
        out.write(capture);
    }
    "--"         {
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

<LSTRING, COMMENT> {
    "]" [=]* "]"    {
        String capture = yytext();
        out.write(capture);
        if (LuaUtils.isClosingLongBracket(capture, bracketLevel)) yypop();
    }
}

<STRING, QSTRING, LSTRING> {
    {WhspChar}*{EOL}    {
        disjointSpan(null);
        startNewLine();
        disjointSpan(HtmlConsts.STRING_CLASS);
    }
}

<COMMENT> {
    {WhspChar}*{EOL}    {
        disjointSpan(null);
        startNewLine();
        disjointSpan(HtmlConsts.COMMENT_CLASS);
    }
}

<SCOMMENT> {
    {WhspChar}*{EOL}    {
        yypop();
        startNewLine();
    }
}

<YYINITIAL, STRING, LSTRING, COMMENT, SCOMMENT, QSTRING> {
    [&<>\'\"]          { out.write(htmlize(yytext())); }
    {WhspChar}*{EOL}   { startNewLine(); }
    {WhiteSpace}       { out.write(yytext());           }
    [!-~]              { out.write(yytext()); }
    [^\n]              { writeUnicodeChar(yycharat(0)); }
}

<STRING, LSTRING, COMMENT, SCOMMENT, QSTRING> {
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
    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+    {
        writeEMailAddress(yytext());
    }
}

<STRING, LSTRING, COMMENT, SCOMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}
<QSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.APOS_NO_BSESC);
    }
}
