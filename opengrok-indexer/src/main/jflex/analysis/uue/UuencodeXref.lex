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
 * Copyright (c) 2012, 2013 Constantine A. Murenin <C++@Cns.SU>
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.uue;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.EmphasisHint;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class UuencodeXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh

%state MODE NAME UUE

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%%
<YYINITIAL> {
  ^ ( "begin " | "begin-base64 " ) {
    yybegin(MODE);
    yypushback(1);
    onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
  }

  {BrowseableURI}    {
    onUriMatched(yytext(), yychar);
  }

  {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+    {
    onEmailAddressMatched(yytext(), yychar);
  }

  {FNameChar}+ { onNonSymbolMatched(yytext(), yychar); }

  {EOL}    { onEndOfLineMatched(yytext(), yychar); }

  {WhspChar} |
  [^\n]    { onNonSymbolMatched(yytext(), yychar); }
}

<MODE> {
  [ ] { onNonSymbolMatched(yytext(), yychar); }
  [^ \n]+ " " {
    yybegin(NAME);
    yypushback(1);
    onNonSymbolMatched(yytext(), EmphasisHint.EM, yychar);
  }
  [^] { yybegin(YYINITIAL); yypushback(1); }
}

<NAME>{
  [ ] { onNonSymbolMatched(yytext(), yychar); }
  [^ \n]+\n {
    yybegin(UUE);
    yypushback(1);
    String t = yytext();
    onQueryTermMatched(t, yychar);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
  }
  [^] { yybegin(YYINITIAL); yypushback(1); }
}

<UUE> {
  ^ ( "end" | "====" ) \n {
    yybegin(YYINITIAL);
    yypushback(1);
    onDisjointSpanChanged(null, yychar);
    onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
  }

  {EOL}    {onEndOfLineMatched(yytext(), yychar); }
  [^\n\r]+    { onNonSymbolMatched(yytext(), yychar); }
}
