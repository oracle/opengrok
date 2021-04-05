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
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets Lisp symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.lisp;

import java.util.Locale;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class LispSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char

%{
    private int nestedComment;
%}

Identifier = [\-\+\*\!\@\$\%\&\/\?\.\,\:\{\}\=a-zA-Z0-9_\<\>]+

%state STRING COMMENT SCOMMENT

%%

<YYINITIAL> {
{Identifier} {String id = yytext();
              if (!Consts.kwd.contains(id.toLowerCase(Locale.ROOT))) {
                        onSymbolMatched(id, yychar);
                        return yystate(); }
              }
 \"     { yybegin(STRING); }
";"     { yybegin(SCOMMENT); }
}

<STRING> {
 \"     { yybegin(YYINITIAL); }
\\\\ | \\\"     {}
}

<YYINITIAL, COMMENT> {
 "#|"    { yybegin(COMMENT); ++nestedComment; }
}

<COMMENT> {
"|#"    { if (--nestedComment == 0) { yybegin(YYINITIAL); } }
}

<SCOMMENT> {
\n      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT, SCOMMENT> {
[^]    {}
}
