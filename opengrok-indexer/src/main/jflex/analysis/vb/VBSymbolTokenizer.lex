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
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets VB symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.vb;

import java.util.Locale;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class VBSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%include ../CommonLexer.lexh
%char

%state STRING COMMENT

%include ../Common.lexh
%include VB.lexh
%%

<YYINITIAL> {
{Identifier} {
    String id = yytext();
    if (!Consts.reservedKeywords.contains(id.toLowerCase(Locale.ROOT))) {
                        onSymbolMatched(id, yychar);
                        return yystate(); }
              }

 {Number}    {}

 \"     { yybegin(STRING); }
 \'     { yybegin(COMMENT); }
}

<STRING> {
   \"\"    {}
   \"     { yybegin(YYINITIAL); }
}

<COMMENT> {
{WhspChar}+    {}
{EOL}     { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT> {
[^]    {}
}
