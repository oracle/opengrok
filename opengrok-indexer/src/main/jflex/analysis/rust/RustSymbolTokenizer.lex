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
 * Gets Rust symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.rust;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class RustSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char
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
%}

%state STRING RSTRING COMMENT SCOMMENT

%include ../Common.lexh
%include Rust.lexh
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
 [b]?\"     { yybegin(STRING); }
 [b]?[r][#]*\" {
    yybegin(RSTRING);
    rawHashCount = RustUtils.countRawHashes(yytext());
 }
 [b]?\' ([^\n\r\'\\] | \\[^\n\r]) \' |
 [b]?\' \\[xX]{HEXDIG}{HEXDIG} \' |
 [b]?\' \\[uU]\{ {HEXDIG}{1,6} \}\'    {}
 "/*"   {
    ++nestedComment;
    yybegin(COMMENT);
 }
 "//"   { yybegin(SCOMMENT); }
}

<STRING> {
 \\[\"\\]    {}
 \"     { yybegin(YYINITIAL); }
}

<RSTRING> {
    \"[#]*    {
        String capture = yytext();
        if (RustUtils.isRawEnding(capture, rawHashCount)) {
            yybegin(YYINITIAL);
            int excess = capture.length() - 1 - rawHashCount;
            if (excess > 0) yypushback(excess);
        }
    }
}

<STRING, RSTRING> {
    {WhspChar}*{EOL}    {
        // no-op
    }
}

<COMMENT> {
    "*/"    { if (--nestedComment == 0) yybegin(YYINITIAL); }
    "/*"    { ++nestedComment; }
}

<SCOMMENT> {
{WhspChar}+    {}
{EOL}      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, RSTRING, COMMENT, SCOMMENT> {
[^]    {}
}
