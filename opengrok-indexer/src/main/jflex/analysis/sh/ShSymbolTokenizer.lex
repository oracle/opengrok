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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.sh;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class ShSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char

%state STRING COMMENT SCOMMENT QSTRING

%include ../Common.lexh
%include Sh.lexh
%%

<YYINITIAL> {
{Identifier}    {
    String id = yytext();
                if(!Consts.shkwd.contains(id)){
                        onSymbolMatched(id, yychar);
                        return yystate(); }
              }
 {Number}    {}
 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 "#"    { yybegin(SCOMMENT); }

 {Unary_op_req_lookahead} / \W    {
    // noop
 }
 {Unary_op_req_lookahead} $    {
    // noop
 }
 {WhspChar}+ {Unary_op_char} / ")"    {
    // noop
 }
 {Binary_op}    {
    // noop
 }
}

<STRING> {
"$" {Identifier}    {
    onSymbolMatched(yytext().substring(1), yychar + 1);
    return yystate();
 }

"${" {Identifier} "}"    {
    int startOffset = 2;            // trim away the "${" prefix
    int endOffset = yylength() - 1; // trim away the "}" suffix
    onSymbolMatched(yytext().substring(startOffset, endOffset), yychar +
        startOffset);
    return yystate();
 }

 \"     { yybegin(YYINITIAL); }
 \\[\"\$\`\\]    {}
}

<QSTRING> {
 \\[\']    {}
 \'     { yybegin(YYINITIAL); }
}

<SCOMMENT> {
{WhspChar}+    {}
{EOL}      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, SCOMMENT, QSTRING> {
[^]    {}
}
