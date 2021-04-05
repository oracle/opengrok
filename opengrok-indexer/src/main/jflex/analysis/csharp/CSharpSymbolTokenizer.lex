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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets C# symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.csharp;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class CSharpSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char

%state STRING COMMENT SCOMMENT QSTRING VSTRING

%include ../Common.lexh
%include CSharp.lexh
%%

<YYINITIAL> {
{Identifier} {
    String id = yytext();
                if(!Consts.kwd.contains(id)){
                        onSymbolMatched(id, yychar);
                        return yystate();
                }
 }

 {Number}    {}

 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 "/*"   { yybegin(COMMENT); }
 "//"   { yybegin(SCOMMENT); }
 "@\""  { yybegin(VSTRING); }  
}

<STRING> {
 \\[\"\\]    {}
 \"     { yybegin(YYINITIAL); }
}

<QSTRING> {
 \\[\'\\]    {}
 \'     { yybegin(YYINITIAL); }
}

<VSTRING> {
 \\ |
 \"\"    {}
 \"    { yybegin(YYINITIAL);}
}

<COMMENT> {
"*/"    { yybegin(YYINITIAL);}
}

<SCOMMENT> {
{CsharpEOL}    { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING, VSTRING> {
{WhspChar}+    {}
[^]    {}
}
