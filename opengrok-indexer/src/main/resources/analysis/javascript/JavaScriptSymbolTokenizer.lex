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
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets JavaScript symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.javascript;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class JavaScriptSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%buffer 32766
%init{
    yyline = 1;
%init}
%int
%include CommonLexer.lexh
%char

%state STRING REGEXP_START REGEXP COMMENT SCOMMENT QSTRING

%include JavaScript.lexh
%%

<YYINITIAL> {
{Identifier} {String id = yytext();
                if(!Consts.kwd.contains(id)){
                        onSymbolMatched(id, yychar);
                        return yystate(); }
              }
 {Number}    {}
 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 /*
  * Literal regexps are in conflict with division "/" and are detected
  * in javascript based on context and when ambiguous, the division has
  * a higher precedence. We do a best-effort context matching for
  * preceding "=" (variable), "(" (function call) or ":" (object).
  */
 [:=(][ \t\r\n]*/\/    { yybegin(REGEXP_START); }
 "/*"   { yybegin(COMMENT); }
 "//"   { yybegin(SCOMMENT); }
}

<STRING> {
 \\[\"\\]    {}
 \"     { yybegin(YYINITIAL); }
}

<REGEXP_START> {
    \/  { yybegin(REGEXP); }
}

<REGEXP> {
    \\[/]   {}
    \/[gimsuy]* { yybegin(YYINITIAL); }
}

<QSTRING> {
 \\[\'\\]    {}
 \'     { yybegin(YYINITIAL); }
}

<COMMENT> {
"*/"    { yybegin(YYINITIAL);}
}

<SCOMMENT> {
\n      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, REGEXP_START, REGEXP, COMMENT, SCOMMENT, QSTRING> {
[^]    {}
}
