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
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Lua file
 */

package org.opengrok.indexer.analysis.lua;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;

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
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
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

    protected void chkLOC() {
        switch (yystate()) {
            case COMMENT:
            case SCOMMENT:
                break;
            default:
                phLOC();
                break;
        }
    }
%}

File = [a-zA-Z]{FNameChar}* "." ([Ll][Uu][Aa] | [Tt][Xx][Tt] |
    [Hh][Tt][Mm][Ll]? | [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state STRING LSTRING COMMENT SCOMMENT QSTRING

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Lua.lexh
%%
<YYINITIAL> {
    {Identifier} {
        chkLOC();
        String id = yytext();
        onFilteredSymbolMatched(id, yychar, Consts.kwd);
    }
    {Number}     {
        chkLOC();
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
    }
    \"           {
        chkLOC();
        yypush(STRING);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
    "[" [=]* "["    {
        chkLOC();
        String capture = yytext();
        bracketLevel = LuaUtils.countOpeningLongBracket(capture);
        yypush(LSTRING);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(capture, yychar);
    }
    \'           {
        chkLOC();
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
    chkLOC();
    onNonSymbolMatched("<", yychar);
    String path = yytext();
    path = path.substring(1, path.length() - 1);
    onFilelikeMatched(path, yychar + 1);
    onNonSymbolMatched(">", yychar + 1 + path.length());
}

<STRING> {
    \\[\"\\] |
    \" {WhspChar}+ \"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
    \"    {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypop();
    }
}

<QSTRING> {
    \\[\'\\] |
    \' {WhspChar}+ \'    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
    \'    {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypop();
    }
}

<LSTRING, COMMENT> {
    "]" [=]* "]"    {
        chkLOC();
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
    [[\s]--[\n]]       { onNonSymbolMatched(yytext(), yychar); }
    [^\n]              { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING, LSTRING, COMMENT, SCOMMENT, QSTRING> {
    {FPath}    {
        chkLOC();
        onPathlikeMatched(yytext(), '/', false, yychar);
    }
    {File} {
        chkLOC();
        String path = yytext();
        onFilelikeMatched(path, yychar);
    }
    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+    {
        chkLOC();
        onEmailAddressMatched(yytext(), yychar);
    }
}

<STRING, LSTRING, COMMENT, SCOMMENT> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar);
    }
}
<QSTRING> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar, StringUtils.APOS_NO_BSESC);
    }
}
