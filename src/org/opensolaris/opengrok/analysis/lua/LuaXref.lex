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

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;

/**
 * @author Evan Kinney
 */
%%
%public
%class LuaXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%init{
    yyline = 1;
%init}
%include CommonLexer.lexh
%{
    int bracketLevel;

    @Override
    public void reset() {
        super.reset();
        bracketLevel = 0;
    }

    @Override
    public void yypop() throws IOException {
        onDisjointSpanChanged(null, yychar);
        super.yypop();
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
        onFilteredSymbolMatched(id, yychar, Consts.kwd);
    }
    {Number}     {
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
    }
    \"           {
        yypush(STRING);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
    "[" [=]* "["    {
        String capture = yytext();
        bracketLevel = LuaUtils.countOpeningLongBracket(capture);
        yypush(LSTRING);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(capture, yychar);
    }
    \'           {
        yypush(QSTRING);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
    "--[" [=]* "["    {
        String capture = yytext();
        String bracket = capture.substring(2);
        bracketLevel = LuaUtils.countOpeningLongBracket(bracket);
        yypush(COMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched(capture, yychar);
    }
    "--"         {
        yypush(SCOMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
}

"<" ({File}|{FPath}) ">" {
    onNonSymbolMatched("<", yychar);
    String path = yytext();
    path = path.substring(1, path.length() - 1);
    onFilelikeMatched(path, yychar + 1);
    onNonSymbolMatched(">", yychar + 1 + path.length());
}

<STRING> {
    \\[\"\\] |
    \" {WhspChar}+ \"    { onNonSymbolMatched(yytext(), yychar); }
    \"    {
        onNonSymbolMatched(yytext(), yychar);
        yypop();
    }
}

<QSTRING> {
    \\[\'\\] |
    \' {WhspChar}+ \'    { onNonSymbolMatched(yytext(), yychar); }
    \'    {
        onNonSymbolMatched(yytext(), yychar);
        yypop();
    }
}

<LSTRING, COMMENT> {
    "]" [=]* "]"    {
        String capture = yytext();
        onNonSymbolMatched(capture, yychar);
        if (LuaUtils.isClosingLongBracket(capture, bracketLevel)) yypop();
    }
}

<STRING, QSTRING, LSTRING> {
    {WhspChar}*{EOL}    {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    }
}

<COMMENT> {
    {WhspChar}*{EOL}    {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    }
}

<SCOMMENT> {
    {WhspChar}*{EOL}    {
        yypop();
        onEndOfLineMatched(yytext(), yychar);
    }
}

<YYINITIAL, STRING, LSTRING, COMMENT, SCOMMENT, QSTRING> {
    {WhspChar}*{EOL}   { onEndOfLineMatched(yytext(), yychar); }
    [^\n]              { onNonSymbolMatched(yytext(), yychar); }
}

<STRING, LSTRING, COMMENT, SCOMMENT, QSTRING> {
    {FPath}    { onPathlikeMatched(yytext(), '/', false, yychar); }
    {File} {
        String path = yytext();
        onFilelikeMatched(path, yychar);
    }
    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+    {
        onEmailAddressMatched(yytext(), yychar);
    }
}

<STRING, LSTRING, COMMENT, SCOMMENT> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar);
    }
}
<QSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, StringUtils.APOS_NO_BSESC);
    }
}
