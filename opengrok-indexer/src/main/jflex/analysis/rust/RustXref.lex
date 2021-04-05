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
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2016 Nikolay Denev.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Rust file
 */

package org.opengrok.indexer.analysis.rust;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.ScopeAction;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class RustXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
  /**
   * Stores the number of hashes beginning and ending a raw string or raw byte
   * string. E.g., r##"blah"## has rawHashCount == 2.
   */
  int rawHashCount;

  int nestedComment;

  @Override
  public void reset() {
      super.reset();
      rawHashCount = 0;
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

File = [a-zA-Z]{FNameChar}* "." ([Rr][Ss] | [Cc][Oo][Nn][Ff] | [Tt][Xx][Tt] |
    [Hh][Tt][Mm][Ll]? | [Xx][Mm][Ll] | [Ii][Nn][Ii] | [Dd][Ii][Ff][Ff] |
    [Pp][Aa][Tt][Cc][Hh])

%state  STRING RSTRING COMMENT SCOMMENT

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Rust.lexh
%%
<YYINITIAL> {
    \{ { chkLOC(); onScopeChanged(ScopeAction.INC, yytext(), yychar); }
    \} { chkLOC(); onScopeChanged(ScopeAction.DEC, yytext(), yychar); }
    \; { chkLOC(); onScopeChanged(ScopeAction.END, yytext(), yychar); }
    {Identifier} {
        chkLOC();
        String id = yytext();
        onFilteredSymbolMatched(id, yychar, Consts.kwd);
    }
    "<" ({File}|{FPath}) ">" {
        chkLOC();
        onNonSymbolMatched("<", yychar);
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        onFilelikeMatched(path, yychar + 1);
        onNonSymbolMatched(">", yychar + 1 + path.length());
    }
    {Number} {
        chkLOC();
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
    }
    [b]?\" {
        chkLOC();
        yypush(STRING);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
    [b]?[r][#]*\" {
        chkLOC();
        yypush(RSTRING);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        String capture = yytext();
        onNonSymbolMatched(capture, yychar);
        rawHashCount = RustUtils.countRawHashes(capture);
    }
    [b]?\' ([^\n\r\'\\] | \\[^\n\r]) \' |
    [b]?\' \\[xX]{HEXDIG}{HEXDIG} \' |
    [b]?\' \\[uU]\{ {HEXDIG}{1,6} \}\'    {
        chkLOC();
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
    }
    "//" {
        yypush(SCOMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
    "/*"  {
        ++nestedComment;
        yypush(COMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
}

<STRING> {
    \\[\"\\]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
    \"    {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypop();
    }
}

<RSTRING> {
    \"[#]*    {
        chkLOC();
        String capture = yytext();
        if (RustUtils.isRawEnding(capture, rawHashCount)) {
            String ender = capture.substring(0, 1 + rawHashCount);
            onNonSymbolMatched(ender, yychar);
            yypop();
            int excess = capture.length() - ender.length();
            if (excess > 0) yypushback(excess);
        } else {
            onNonSymbolMatched(capture, yychar);
        }
    }
}

<STRING, RSTRING> {
    {WhspChar}*{EOL}    {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    }
}

<COMMENT> {
    "*/"    {
        onNonSymbolMatched(yytext(), yychar);
        if (--nestedComment == 0) yypop();
    }
    "/*"    {
        ++nestedComment;
        onNonSymbolMatched(yytext(), yychar);
    }
}

<SCOMMENT> {
    {WhspChar}*{EOL} {
        yypop();
        onEndOfLineMatched(yytext(), yychar);
    }
}

<YYINITIAL, STRING, RSTRING, COMMENT, SCOMMENT> {
    {WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
    [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
    [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING, SCOMMENT> {
    {FPath}    {
        chkLOC();
        onPathlikeMatched(yytext(), '/', false, yychar);
    }

    {File} {
        chkLOC();
        String path = yytext();
        onFilelikeMatched(path, yychar);
    }

    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+ {
        chkLOC();
        onEmailAddressMatched(yytext(), yychar);
    }
}

<STRING, RSTRING, SCOMMENT> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar);
    }
}

<COMMENT> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, StringUtils.END_C_COMMENT);
    }
}
