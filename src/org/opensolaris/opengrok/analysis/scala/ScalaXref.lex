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
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Scala file
 */

package org.opensolaris.opengrok.analysis.scala;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.analysis.EmphasisHint;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class ScalaXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%init{
    yyline = 1;
%init}
%include CommonLexer.lexh
%{
  /* Must match {WhspChar}+ regex */
  private final static String WHITE_SPACE = "[ \\t\\f]+";

  private int nestedComment;

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

  private void pushQuotedString(int state, String capture) throws IOException {
      int qoff = capture.indexOf("\"");
      String id = capture.substring(0, qoff);
      String quotes = capture.substring(qoff);
      onNonSymbolMatched(id, yychar);
      yypush(state);
      onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
      onNonSymbolMatched(quotes, yychar);
  }
%}

File = [a-zA-Z]{FNameChar}* "." ([Ss][Cc][Aa][Ll][Aa] |
    [Pp][Rr][Oo][Pp][Ee][Rr][Tt][Ii][Ee][Ss] | [Pp][Rr][Oo][Pp][Ss] |
    [Xx][Mm][Ll] | [Cc][Oo][Nn][Ff] | [Tt][Xx][Tt] | [Hh][Tt][Mm][Ll]? |
    [Ii][Nn][Ii] | [Jj][Nn][Ll][Pp] | [Jj][Aa][Dd] | [Dd][Ii][Ff][Ff] |
    [Pp][Aa][Tt][Cc][Hh])

JavadocWithClassArg = "@throws" | "@exception"
JavadocWithParamNameArg = "@param"

ClassName = ({Identifier} ".")* {Identifier}
ParamName = {Identifier} | "<" {Identifier} ">"

/*
 * STRING : string literal
 * ISTRING : string literal with interpolation
 * MSTRING : multi-line string literal
 * IMSTRING : multi-line string literal with interpolation
 * QSTRING : character literal
 * SCOMMENT : single-line comment
 * COMMENT : multi-line comment
 * JAVADOC : multi-line comment with JavaDoc conventions
 */
%state STRING ISTRING MSTRING IMSTRING QSTRING SCOMMENT COMMENT JAVADOC

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Scala.lexh
%%
<YYINITIAL>{

{Identifier} {
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
}

 {BacktickIdentifier} {
    String capture = yytext();
    String id = capture.substring(1, capture.length() - 1);
    onNonSymbolMatched("`", yychar);
    onFilteredSymbolMatched(id, yychar, null);
    onNonSymbolMatched("`", yychar);
 }

 {OpSuffixIdentifier}    {
    String capture = yytext();
    int uoff = capture.lastIndexOf("_");
    // ctags include the "_" in the symbol, so follow that too.
    String id = capture.substring(0, uoff + 1);
    String rest = capture.substring(uoff + 1);
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
    onNonSymbolMatched(rest, yychar);
 }

"<" ({File}|{FPath}) ">" {
        onNonSymbolMatched("<", yychar);
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        onFilelikeMatched(path, yychar + 1);
        onNonSymbolMatched(">", yychar + 1 + path.length());
}

/*{Hier}
        { onPathlikeMatched(yytext(), '.', false, yychar); }
*/
 {Number}        {
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 ([fs] | "raw") \"    {
    pushQuotedString(ISTRING, yytext());
 }
 {Identifier}? \"    {
    pushQuotedString(STRING, yytext());
 }
 \'     {
    yypush(QSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 ([fs] | "raw") \"\"\"    {
    pushQuotedString(IMSTRING, yytext());
 }
 {Identifier}? \"\"\"    {
    pushQuotedString(MSTRING, yytext());
 }
 "/*" "*"+ "/"    {
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
 "/*" "*"+    {
    if (nestedComment++ == 0) {
        yypush(JAVADOC);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    }
    onNonSymbolMatched(yytext(), yychar);
 }
 "//"   {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING, ISTRING> {
 \\[\"\\]    { onNonSymbolMatched(yytext(), yychar); }
 \"     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<ISTRING, IMSTRING> {
    /*
     * TODO : support "arbitrary expressions" inside curly brackets
     */
    \$ {Identifier}    {
        String capture = yytext();
        String sigil = capture.substring(0, 1);
        String id = capture.substring(1);
        onNonSymbolMatched(sigil, yychar);
        onDisjointSpanChanged(null, yychar);
        onFilteredSymbolMatched(id, yychar, Consts.kwd);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    }
}

<QSTRING> {
 \\[\'\\]    { onNonSymbolMatched(yytext(), yychar); }
 \'     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<MSTRING, IMSTRING> {
 /*
  * For multi-line string, "Unicode escapes work as everywhere else, but none
  * of the escape sequences [in 'Escape Sequences'] are interpreted."
  */
 \"\"\"    {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<YYINITIAL, COMMENT, JAVADOC> {
    "/*"    {
        if (nestedComment++ == 0) {
            yypush(COMMENT);
            onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        }
        onNonSymbolMatched(yytext(), yychar);
    }
}

<COMMENT, JAVADOC> {
 "*/"    {
    onNonSymbolMatched(yytext(), yychar);
    if (--nestedComment == 0) {
        yypop();
    }
 }
 {WhspChar}*{EOL}    {
    onDisjointSpanChanged(null, yychar);
    onEndOfLineMatched(yytext(), yychar);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
 }
}

<JAVADOC> {
  {JavadocWithParamNameArg} {WhspChar}+ {ParamName} |
  {JavadocWithClassArg} {WhspChar}+ {ClassName} {
    String text = yytext();
    String[] tokens = text.split(WHITE_SPACE, 2);
    onNonSymbolMatched(tokens[0], EmphasisHint.STRONG, yychar);
    onNonSymbolMatched(text.substring(tokens[0].length(), text.length() -
        tokens[1].length()), yychar);
    onNonSymbolMatched(tokens[1], EmphasisHint.EM, yychar);
  }
  "@" {Identifier} {
    onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
  }
}

<SCOMMENT> {
  {WhspChar}*{EOL} {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
}

<YYINITIAL> {
 {OpIdentifier}    {
    onNonSymbolMatched(yytext(), yychar);
 }
}

<YYINITIAL, STRING, ISTRING, MSTRING, IMSTRING, COMMENT, SCOMMENT, QSTRING,
    JAVADOC> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [^\n]    { onNonSymbolMatched(yytext(), yychar); }
}

<STRING, MSTRING, COMMENT, SCOMMENT, QSTRING, JAVADOC> {
{FPath}
        { onPathlikeMatched(yytext(), '/', false, yychar); }

{File}
        {
        String path = yytext();
        onFilelikeMatched(path, yychar);
 }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          onEmailAddressMatched(yytext(), yychar);
        }
}

<STRING, MSTRING, QSTRING, SCOMMENT> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar);
    }
}

<ISTRING, IMSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, ScalaUtils.DOLLAR_SIGN);
    }
}

<COMMENT, JAVADOC> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, StringUtils.END_C_COMMENT);
    }
}
