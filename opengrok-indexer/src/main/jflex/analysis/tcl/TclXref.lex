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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Tcl file
 */

package org.opengrok.indexer.analysis.tcl;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class TclXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
  private int braceCount;

  @Override
  public void reset() {
      super.reset();
      braceCount = 0;
  }

  @Override
  public void yypop() throws IOException {
      onDisjointSpanChanged(null, yychar);
      super.yypop();
  }

  /**
   * Write {@code whsp} to the {@code xref} output -- if the whitespace does
   * not contain any LFs then the full String is written; otherwise, pre-LF
   * spaces are condensed as usual.
   * @param xref the target instance
   * @param whsp a defined whitespace capture
   * @throws java.io.IOException if an output error occurs
   */
  private void writeWhitespace(String whsp) throws IOException {
      int i;
      if ((i = whsp.indexOf("\n")) == -1) {
          onNonSymbolMatched(whsp, yychar);
      } else {
          int numlf = 1, off = i + 1;
          while ((i = whsp.indexOf("\n", off)) != -1) {
              ++numlf;
              off = i + 1;
          }
          while (numlf-- > 0) onEndOfLineMatched("\n", yychar);
          if (off < whsp.length()) {
              onNonSymbolMatched(whsp.substring(off), yychar);
          }
      }
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

%state STRING COMMENT SCOMMENT BRACES VARSUB2

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Tcl.lexh
%%
<YYINITIAL>{

 [\{]    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    ++braceCount;
    yypush(BRACES);
 }
}

<YYINITIAL, BRACES> {
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
 "#"    {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 {WordOperators}    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
 }
}

<YYINITIAL, STRING, BRACES> {
    {Backslash_sub}    {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
    }
    {Backslash_nl}    {
        chkLOC();
        String capture = yytext();
        String esc = capture.substring(0, 1);
        String whsp = capture.substring(1);
        onNonSymbolMatched(esc, yychar);
        writeWhitespace(whsp);
    }
    {Varsub1}    {
        chkLOC();
        String capture = yytext();
        String sigil = capture.substring(0, 1);
        String name = capture.substring(1);
        onNonSymbolMatched(sigil, yychar);
        onFilteredSymbolMatched(name, yychar, Consts.kwd);
    }
    {Varsub2}    {
        chkLOC();
        // TclXref could get away without VARSUB2 as a state, but for ease in
        // comparing to TclSymbolTokenizer, it is modeled here too.
        yypush(VARSUB2);
        String capture = yytext();
        String sigil = capture.substring(0, 1);
        int lparen_i = capture.indexOf("(");
        String name1 = capture.substring(1, lparen_i);
        yypushback(capture.length() - lparen_i - 1);
        onNonSymbolMatched(sigil, yychar);
        if (name1.length() > 0) {
            onFilteredSymbolMatched(name1, yychar, Consts.kwd);
        }
        onNonSymbolMatched("(", yychar);
    }
    {Varsub3}    {
        chkLOC();
        String capture = yytext();
        String sigil = capture.substring(0, 2);
        String name = capture.substring(2, capture.length() - 1);
        String endtoken = capture.substring(capture.length() - 1);
        onNonSymbolMatched(sigil, yychar);
        onFilteredSymbolMatched(name, yychar, Consts.kwd);
        onNonSymbolMatched(endtoken, yychar);
    }
}

<VARSUB2> {
    {name_unit}+    {
        chkLOC();
        String name2 = yytext();
        yypop();
        onFilteredSymbolMatched(name2, yychar, Consts.kwd);
    }
}

<YYINITIAL, BRACES> {
    {OrdinaryWord}    {
        chkLOC();
        String id = yytext();
        onFilteredSymbolMatched(id, yychar, Consts.kwd);
    }
}

<STRING> {
 \"     {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<BRACES> {
    [\}]    {
        chkLOC();
        if (--braceCount == 0) {
            yypop();
        }
        onNonSymbolMatched(yytext(), yychar);
    }
    [\{]    {
        chkLOC();
        ++braceCount;
        onNonSymbolMatched(yytext(), yychar);
    }
}

<SCOMMENT> {
  {WhspChar}*{EOL}    {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, BRACES> {
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
