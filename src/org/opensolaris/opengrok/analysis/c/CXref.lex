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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a C file
 */

package org.opensolaris.opengrok.analysis.c;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.analysis.ScopeAction;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class CXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%init{
    yyline = 1;
%init}
%include CommonLexer.lexh
%{
  private static final Pattern MATCH_INCLUDE = Pattern.compile(
      "^(#.*)(include)(.*)([<\"])(.*)([>\"])$");
  private static final int INCL_HASH_G = 1;
  private static final int INCLUDE_G = 2;
  private static final int INCL_POST_G = 3;
  private static final int INCL_PUNC0_G = 4;
  private static final int INCL_PATH_G = 5;
  private static final int INCL_PUNCZ_G = 6;

  @Override
  public void yypop() throws IOException {
      onDisjointSpanChanged(null, yychar);
      super.yypop();
  }
%}

File = [a-zA-Z]{FNameChar}* "." ([cChHsStT] | [Cc][Oo][Nn][Ff] |
    [Jj][Aa][Vv][Aa] | [CcHh][Pp][Pp] | [Cc][Cc] | [Tt][Xx][Tt] |
    [Hh][Tt][Mm][Ll]? | [Pp][Ll] | [Xx][Mm][Ll] | [CcHh][\+][\+] | [Hh][Hh] |
    [CcHh][Xx][Xx] | [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state  STRING COMMENT SCOMMENT QSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include C.lexh
%%
<YYINITIAL>{
 \{     { onScopeChanged(ScopeAction.INC, yytext(), yychar); }
 \}     { onScopeChanged(ScopeAction.DEC, yytext(), yychar); }
 \;     { onScopeChanged(ScopeAction.END, yytext(), yychar); }

{Identifier} {
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
}

"#" {WhspChar}* "include" {WhspChar}* ("<"[^>\n\r]+">" | \"[^\"\n\r]+\")    {
        String capture = yytext();
        Matcher match = MATCH_INCLUDE.matcher(capture);
        if (match.matches()) {
            onNonSymbolMatched(match.group(INCL_HASH_G), yychar);
            onFilteredSymbolMatched(match.group(INCLUDE_G), yychar, Consts.kwd);
            onNonSymbolMatched(match.group(INCL_POST_G), yychar);
            onNonSymbolMatched(match.group(INCL_PUNC0_G), yychar);
            String path = match.group(INCL_PATH_G);
            onPathlikeMatched(path, '/', false, yychar);
            onNonSymbolMatched(match.group(INCL_PUNCZ_G), yychar);
        } else {
            onNonSymbolMatched(capture, yychar);
        }
}

/*{Hier}
        { onPathlikeMatched(yytext(), '.', false, yychar); }
*/
{Number} {
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \\[\"\']    { onNonSymbolMatched(yytext(), yychar); }
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
 "/*"   {
    yypush(COMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 "//"   {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \\[\"\\] |
 \" {WhspChar}+ \"    { onNonSymbolMatched(yytext(), yychar); }
 \"    { onNonSymbolMatched(yytext(), yychar); yypop(); }
}

<QSTRING> {
 \\[\'\\] |
 \' {WhspChar}+ \'    { onNonSymbolMatched(yytext(), yychar); }

 \'    { onNonSymbolMatched(yytext(), yychar); yypop(); }
}

<COMMENT> {
"*/"    { onNonSymbolMatched(yytext(), yychar); yypop(); }
}

<SCOMMENT> {
{WhspChar}*{EOL}      {
    yypop();
    onEndOfLineMatched(yytext(), yychar);}
}


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [^\n]    { onNonSymbolMatched(yytext(), yychar); }
}

<STRING, COMMENT, SCOMMENT, QSTRING> {
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

<STRING, SCOMMENT> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar);
    }
}

<COMMENT> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, StringUtils.END_C_COMMENT);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, StringUtils.APOS_NO_BSESC);
    }
}
