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
 * Cross reference a Lisp file
 */

package org.opengrok.indexer.analysis.lisp;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class LispXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
  private int nestedComment;

  /**
   * Resets the Lisp tracked state; {@inheritDoc}
   */
  @Override
  public void reset() {
      super.reset();
      nestedComment = 0;
  }

  protected void chkLOC() {
      switch (yystate()) {
          case COMMENT:
          case SCOMMENT:
              break;
          default:
              phLOC();
              break;
      }
  }
%}

Identifier = [\-\+\*\!\@\$\%\&\/\?\.\,\:\{\}\=a-zA-Z0-9_\<\>]+

File = [a-zA-Z] {FNameChar}+ "." ([a-zA-Z]+)

Number = ([0-9]+\.[0-9]+|[0-9][0-9]*|"#" [boxBOX] [0-9a-fA-F]+)

%state  STRING COMMENT SCOMMENT

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%%
<YYINITIAL>{

{Identifier} {
    chkLOC();
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
}

 {Number}        {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \"     {
    chkLOC();
    yybegin(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched("\"", yychar);
 }
 ";"    {
    yybegin(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(";", yychar);
 }
}

<STRING> {
 \" {WhspChar}+ \"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"     {
    chkLOC();
    yybegin(YYINITIAL);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
 \\[\"\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<YYINITIAL, COMMENT> {
 "#|"   { yybegin(COMMENT);
          if (nestedComment++ == 0) { onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar); }
          onNonSymbolMatched("#|", yychar);
        }
 }

<COMMENT> {
 "|#"   { onNonSymbolMatched("|#", yychar);
          if (--nestedComment == 0) {
            yybegin(YYINITIAL);
            onDisjointSpanChanged(null, yychar);
          }
        }
}

<SCOMMENT> {
  {WhspChar}*{EOL} {
    yybegin(YYINITIAL);
    onDisjointSpanChanged(null, yychar);
    onEndOfLineMatched(yytext(), yychar);
  }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING, COMMENT, SCOMMENT> {
 {FPath}    {
    chkLOC();
    onPathlikeMatched(yytext(), '/', false, yychar);
 }

{File}
        {
        chkLOC();
        String path = yytext();
        onFilelikeMatched(path, yychar);
 }

{BrowseableURI}    {
          chkLOC();
          onUriMatched(yytext(), yychar);
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          chkLOC();
          onEmailAddressMatched(yytext(), yychar);
        }
}
