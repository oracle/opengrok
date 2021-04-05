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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets Erlang symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.erlang;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class ErlangSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char

%state STRING COMMENT QATOM

%include ../Common.lexh
%include Erlang.lexh
%%

<YYINITIAL> {

"?" {Identifier}    {  // Macros
}

{Identifier} {
    String id = yytext();
                if (!id.equals("_") && !Consts.kwd.contains(id)) {
                        onSymbolMatched(id, yychar);
                        return yystate();
                }
 }

^"-" {Identifier} {
    String capture = yytext();
    String punc = capture.substring(0, 1);
    String id = capture.substring(1);
    if (!Consts.modules_kwd.contains(id)) {
        onSymbolMatched(id, yychar + 1);
        return yystate();
    }
}

{ErlInt}        {}
{Number}        {}

 \"     { yybegin(STRING); }
 \'     { yybegin(QATOM); }
 "%"   { yybegin(COMMENT); }
 }

<STRING> {
 \\[\"\\]    {}
 \"     { yybegin(YYINITIAL); }
}

<QATOM> {
 \\[\'\\]    {}
 \'     { yybegin(YYINITIAL); }
}

<COMMENT> {
 {EOL}    { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, QATOM, COMMENT> {
{ErlangWhiteSpace}    {}

[^]    {}
}
