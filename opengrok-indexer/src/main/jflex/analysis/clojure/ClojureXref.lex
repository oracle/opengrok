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
 * Cross reference a Clojure file
 */

package org.opengrok.indexer.analysis.clojure;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class ClojureXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
  private int nestedComment;

  /**
   * Resets the Clojure tracked state; {@inheritDoc}
   */
  @Override
  public void reset() {
      super.reset();
      nestedComment = 0;
  }

  @Override
  public void yypop() throws IOException {
      onDisjointSpanChanged(null, yychar);
      super.yypop();
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

File = [a-zA-Z] {FNameChar}+ "." ([a-zA-Z]+)

%state  STRING COMMENT SCOMMENT

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Clojure.lexh
%%
<YYINITIAL>{

{Identifier} {
    chkLOC();
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
}

 {Number}    {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \"     {
    chkLOC();
    yypush(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 ";"    {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \" {WhspChar}+ \" |
 \\[\"\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"     {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<YYINITIAL, COMMENT> {
 "#|"   {
    if (nestedComment++ == 0) {
        yypush(COMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    }
    onNonSymbolMatched(yytext(), yychar);
 }
}

<COMMENT> {
 "|#"   {
    onNonSymbolMatched(yytext(), yychar);
    if (--nestedComment == 0) {
        yypop();
    }
 }
}

<SCOMMENT> {
  {WhspChar}*{EOL} {
    yypop();
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
