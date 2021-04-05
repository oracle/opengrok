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
 * Get Lua symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.lua;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;

/**
 * @author Evan Kinney
 */
%%
%public
%class LuaSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char
%{
    int bracketLevel;

    @Override
    public void reset() {
        super.reset();
        bracketLevel = 0;
    }
%}

%state STRING LSTRING COMMENT SCOMMENT QSTRING

%include ../Common.lexh
%include Lua.lexh
%%

<YYINITIAL> {
    {Identifier} {
        String id = yytext();
        if (!Consts.kwd.contains(id)) {
            onSymbolMatched(id, yychar);
            return yystate();
        }
    }
    {Number}    {}
    \"     { yybegin(STRING);   }
    "[" [=]* "["    {
        String capture = yytext();
        bracketLevel = LuaUtils.countOpeningLongBracket(capture);
        yybegin(LSTRING);
    }
    \'     { yybegin(QSTRING);  }
    "--[" [=]* "["    {
        String capture = yytext();
        String bracket = capture.substring(2);
        bracketLevel = LuaUtils.countOpeningLongBracket(bracket);
        yybegin(COMMENT);
    }
    "--"   { yybegin(SCOMMENT); }
}

<STRING> {
    \\[\"\\]    {}
    \"   { yybegin(YYINITIAL); }
}

<QSTRING> {
    \\[\'\\]    {}
    \' { yybegin(YYINITIAL); }
}

<LSTRING, COMMENT> {
    "]" [=]* "]"    {
        String capture = yytext();
        if (LuaUtils.isClosingLongBracket(capture, bracketLevel)) {
            yybegin(YYINITIAL);
        }
    }
}

<SCOMMENT> {
    {WhspChar}*{EOL} { yybegin(YYINITIAL); }
}

<YYINITIAL, STRING, LSTRING, COMMENT, SCOMMENT, QSTRING> {
{WhspChar}+    {}
[^] {}
}
