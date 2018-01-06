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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.kotlin;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.analysis.ScopeAction;
import org.opensolaris.opengrok.analysis.EmphasisHint;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class KotlinXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
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
%}

File = [a-zA-Z]{FNameChar}* "." ([Jj][Aa][Vv][Aa] |
    [Pp][Rr][Oo][Pp][Ee][Rr][Tt][Ii][Ee][Ss] | [Pp][Rr][Oo][Pp][Ss] |
    [Xx][Mm][Ll] | [Cc][Oo][Nn][Ff] | [Tt][Xx][Tt] | [Hh][Tt][Mm][Ll]? |
    [Ii][Nn][Ii] | [Jj][Nn][Ll][Pp] | [Jj][Aa][Dd] | [Dd][Ii][Ff][Ff] |
    [Pp][Aa][Tt][Cc][Hh])

KdocWithClassArg = "@throws" | "@exception"
KdocWithParamNameArg = "@param"

ClassName = ({Identifier} ".")* {Identifier}
ParamName = {Identifier} | "<" {Identifier} ">"

%state  STRING COMMENT SCOMMENT QSTRING KDOC TSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Kotlin.lexh
%%
<YYINITIAL>{
 \{     { onScopeChanged(ScopeAction.INC, yytext(), yychar); }
 \}     { onScopeChanged(ScopeAction.DEC, yytext(), yychar); }
 \;     { onScopeChanged(ScopeAction.END, yytext(), yychar); }

{Identifier} {
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
}

"<" ({File}|{FPath}) ">" {
        onNonSymbolMatched("<", yychar);
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        onFilelikeMatched(path, yychar + 1);
        onNonSymbolMatched(">", yychar + 1 + path.length());
}

 {Number}        {
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \"     {
    yypush(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \'     {
    yypush(QSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \"\"\"    {
    yypush(TSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 "/**" / [^/]    {
    if (nestedComment++ == 0) {
        yypush(KDOC);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    }
    onNonSymbolMatched(yytext(), yychar);
 }
 "//"    {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \\[\"\$\\]    { onNonSymbolMatched(yytext(), yychar); }
 \"     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<QSTRING> {
 \\[\'\\] |
 \' {WhspChar}+ \'    { onNonSymbolMatched(yytext(), yychar); }
 \'     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<TSTRING> {
 /*
  * "raw string ... doesn't support backslash escaping"
  */
 \"\"\"    {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<STRING, TSTRING> {
    /*
     * TODO : support template expressions inside curly brackets
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
    {WhspChar}*{EOL}    {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    }
}

<YYINITIAL, COMMENT, KDOC> {
    "/*"    {
        if (nestedComment++ == 0) {
            yypush(COMMENT);
            onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        }
        onNonSymbolMatched(yytext(), yychar);
    }
}

<COMMENT, KDOC> {
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

<KDOC> {
  {KdocWithParamNameArg} {WhspChar}+ {ParamName} |
  {KdocWithClassArg} {WhspChar}+ {ClassName} {
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


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING, KDOC, TSTRING> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [^\n]    { onNonSymbolMatched(yytext(), yychar); }
}

<STRING, COMMENT, SCOMMENT, QSTRING, TSTRING, KDOC> {
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

<STRING, SCOMMENT, QSTRING, TSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar);
    }
}

<COMMENT, KDOC> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, StringUtils.END_C_COMMENT);
    }
}
