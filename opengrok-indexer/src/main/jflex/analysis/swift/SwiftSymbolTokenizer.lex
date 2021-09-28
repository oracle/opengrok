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
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets Swift symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.swift;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class SwiftSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%buffer 32766
%int
%include ../CommonLexer.lexh
%char
%{
    private int nestedComment;

    @Override
    public void reset() {
        super.reset();
        nestedComment = 0;
    }
%}

%state STRING COMMENT SCOMMENT TSTRING

%include ../Common.lexh
%include Swift.lexh
%%

/* TODO : support identifiers escaped by ` `*/
<YYINITIAL> {
{Identifier} {String id = yytext();
                if(!Consts.kwd.contains(id)){
                        onSymbolMatched(id, yychar);
                        return yystate(); }
              }

 [`] {Identifier} [`]    {
    String capture = yytext();
    String id = capture.substring(1, capture.length() - 1);
    onSymbolMatched(id, yychar + 1);
    return yystate();
 }

 {ImplicitIdentifier} {
    // noop
 }

 {Number}    {}

 \"     { yybegin(STRING); }
 \"\"\"   { yybegin(TSTRING); }
 "//"   { yybegin(SCOMMENT); }

}

<STRING> {
 \\[\"\\]    {}
 \"     { yybegin(YYINITIAL); }
}

<TSTRING> {
  \"\"\"     { yybegin(YYINITIAL); }
}

<YYINITIAL, COMMENT> {
 "/*"    {
    if (nestedComment++ == 0) {
        yybegin(COMMENT);
    }
 }
}

<COMMENT> {
 "*/"    {
    if (--nestedComment == 0) {
        yybegin(YYINITIAL);
    }
 }
}

<SCOMMENT> {
 {WhspChar}*{EOL}    { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, TSTRING> {
{WhspChar}+ |
[^]    {}
}
