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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets Tcl symbols - ignores comments, strings, keywords
 */

package org.opensolaris.opengrok.analysis.tcl;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
%%
%public
%class TclSymbolTokenizer
%extends JFlexTokenizer
%unicode
%init{
super(in);
%init}
%int
%include CommonTokenizer.lexh
%char
%{
  private int braceCount;

  @Override
  public void reset() throws IOException {
      super.reset();
      braceCount = 0;
  }
%}

%state STRING COMMENT SCOMMENT BRACES VARSUB2

%include Common.lexh
%include Tcl.lexh
%%

<YYINITIAL> {

 [\{]    {
    ++braceCount;
    yypush(BRACES);
 }
}

<YYINITIAL, BRACES> {
 {Number}    {
    // noop
 }
 \"     { yypush(STRING); }
 "#"    { yypush(SCOMMENT); }
 {WordOperators}    {
    // noop
 }
}

<YYINITIAL, STRING, BRACES> {
    {Backslash_sub}    {
        // noop
    }
    {Backslash_nl}    {
        // noop
    }
    {Varsub1}    {
        String capture = yytext();
        String sigil = capture.substring(0, 1);
        String name = capture.substring(1);
        if (!Consts.kwd.contains(name)) {
            setAttribs(name, yychar + 1, yychar + yylength());
            return yystate();
        }
    }
    {Varsub2}    {
        yypush(VARSUB2);
        String capture = yytext();
        int lparen_i = capture.indexOf("(");
        String name1 = capture.substring(1, lparen_i);
        yypushback(capture.length() - lparen_i - 1);
        if (name1.length() > 0 && !Consts.kwd.contains(name1)) {
            setAttribs(name1, yychar + 1, yychar + lparen_i);
            return yystate();
        }
    }
    {Varsub3}    {
        String capture = yytext();
        String name = capture.substring(2, capture.length() - 1);
        if (!Consts.kwd.contains(name)) {
            setAttribs(name, yychar + 2, yychar + yylength() - 1);
            return yystate();
        }
    }
}

<VARSUB2> {
    {name_unit}+    {
        String name2 = yytext();
        yypop();
        if (!Consts.kwd.contains(name2)) {
            setAttribs(name2, yychar, yychar + yylength());
            return yystate();
        }
    }
}

<YYINITIAL, BRACES> {
    {OrdinaryWord}    {
        String id = yytext();
        if (!Consts.kwd.contains(id)) {
            setAttribs(id, yychar, yychar + yylength());
            return yystate();
        }
    }
}

<STRING> {
 \"     { yypop(); }
}

<BRACES> {
    [\}]    {
        if (--braceCount == 0) {
            yypop();
        }
    }
    [\{]    {
        ++braceCount;
    }
}

<SCOMMENT> {
 {EOL}    { yypop(); }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, BRACES> {
{WhiteSpace} |
[^]    {}
}
