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
 * Gets Scala symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.scala;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class ScalaSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char
%{
    private int nestedComment;

    @Override
    public void reset() {
        super.reset();
        nestedComment = 0;
    }
%}

/*
 * STRING : string literal
 * ISTRING : string literal with interpolation
 * MSTRING : multi-line string literal
 * IMSTRING : multi-line string literal with interpolation
 * QSTRING : character literal
 * SCOMMENT : single-line comment
 * COMMENT : multi-line comment
 */
%state STRING ISTRING MSTRING IMSTRING QSTRING SCOMMENT COMMENT

%include ../Common.lexh
%include Scala.lexh
%%

<YYINITIAL> {
{Identifier} {String id = yytext();
                if(!Consts.kwd.contains(id)){
                        onSymbolMatched(id, yychar);
                        return yystate(); }
              }

 {BacktickIdentifier} {
    String capture = yytext();
    String id = capture.substring(1, capture.length() - 1);
    if (!Consts.kwd.contains(id)) {
        onSymbolMatched(id, yychar + 1);
        return yystate();
    }
 }

 {OpSuffixIdentifier}    {
    String capture = yytext();
    int uoff = capture.lastIndexOf("_");
    // ctags include the "_" in the symbol, so follow that too.
    String id = capture.substring(0, uoff + 1);
    if (!Consts.kwd.contains(id)) {
        onSymbolMatched(id, yychar);
        return yystate();
    }
 }

 {Number}    {}
 ([fs] | "raw") \"    { yybegin(ISTRING); }
 {Identifier}? \"    { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 ([fs] | "raw") \"\"\"    { yybegin(IMSTRING); }
 {Identifier}? \"\"\" { yybegin(MSTRING); }
 "/*" "*"+ "/"    {
    // noop
 }
 "//"   { yybegin(SCOMMENT); }
}

<STRING, ISTRING> {
 \\[\"\\]    {}
 \"     { yybegin(YYINITIAL); }
}

<ISTRING, IMSTRING> {
    /*
     * TODO : support "arbitrary expressions" inside curly brackets
     */
    \$ {Identifier}    {
        String capture = yytext();
        String id = capture.substring(1);
        if (!Consts.kwd.contains(id)) {
            onSymbolMatched(id, yychar + 1);
            return yystate();
       }
    }
}

<QSTRING> {
 \\[\'\\]    {}
 \'     { yybegin(YYINITIAL); }
}

<MSTRING, IMSTRING> {
 /*
  * For multi-line string, "Unicode escapes work as everywhere else, but none
  * of the escape sequences [in 'Escape Sequences'] are interpreted."
  */
 \"\"\"    {
    yybegin(YYINITIAL);;
 }
}
<YYINITIAL, COMMENT> {
    "/*" "*"*    {
        if (nestedComment++ == 0) {
            yybegin(COMMENT);
        }
    }
}

<COMMENT> {
 "*/"    {
    if (--nestedComment == 0) {
        yybegin(YYINITIAL);
    }
 }
}

<SCOMMENT> {
{EOL}      { yybegin(YYINITIAL);}
}

<YYINITIAL> {
 {OpIdentifier}    {
    // noop
 }
}

<YYINITIAL, STRING, ISTRING, MSTRING, IMSTRING, COMMENT, SCOMMENT, QSTRING> {
{WhspChar}+ |
[^]    {}
}
