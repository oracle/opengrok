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

package org.opengrok.indexer.analysis.plain;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class PlainXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh

File = {FNameChar}+ "." ([a-zA-Z]+) {FNameChar}*

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include ../CommonLaxFPath.lexh
%%
{File} | {RelaxedMiddleFPath}
        {
        phLOC();
        String s=yytext();
        onFilelikeMatched(s, yychar);
 }

{BrowseableURI}    {
          phLOC();
          onUriMatched(yytext(), yychar);
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          phLOC();
          onEmailAddressMatched(yytext(), yychar);
        }

// Bug #13362: If there's a very long sequence that matches {FNameChar}+,
// parsing the file will take forever because of all the backtracking. With
// this rule, we avoid much of the backtracking and speed up the parsing
// (in some cases from hours to seconds!). This rule will not interfere with
// the rules above because JFlex always picks the longest match.
{FNameChar}+    { phLOC(); onNonSymbolMatched(yytext(), yychar); }

{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
[[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
[^\n]    { phLOC(); onNonSymbolMatched(yytext(), yychar); }
