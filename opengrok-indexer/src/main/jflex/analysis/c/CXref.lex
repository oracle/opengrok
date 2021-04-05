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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a C file
 */

package org.opengrok.indexer.analysis.c;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.ScopeAction;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class CXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
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

File = [a-zA-Z]{FNameChar}* "." ([cChHsStT] | [Cc][Oo][Nn][Ff] |
    [Jj][Aa][Vv][Aa] | [CcHh][Pp][Pp] | [Cc][Cc] | [Tt][Xx][Tt] |
    [Hh][Tt][Mm][Ll]? | [Pp][Ll] | [Xx][Mm][Ll] | [CcHh][\+][\+] | [Hh][Hh] |
    [CcHh][Xx][Xx] | [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state  STRING COMMENT SCOMMENT QSTRING

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include C.lexh
%%
<YYINITIAL>{
 \{     { chkLOC(); onScopeChanged(ScopeAction.INC, yytext(), yychar); }
 \}     { chkLOC(); onScopeChanged(ScopeAction.DEC, yytext(), yychar); }
 \;     { chkLOC(); onScopeChanged(ScopeAction.END, yytext(), yychar); }

{Identifier} {
    chkLOC();
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
}

"#" {WhspChar}* "include" {WhspChar}* ("<"[^>\n\r]+">" | \"[^\"\n\r]+\")    {
        chkLOC();
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
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \\[\"\']    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"     {
    chkLOC();
    yypush(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \'     {
    chkLOC();
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
 \" {WhspChar}+ \"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); yypop(); }
}

<QSTRING> {
 \\[\'\\] |
 \' {WhspChar}+ \'    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \'    { chkLOC(); onNonSymbolMatched(yytext(), yychar); yypop(); }
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
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING, COMMENT, SCOMMENT, QSTRING> {
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

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          chkLOC();
          onEmailAddressMatched(yytext(), yychar);
        }
}

<STRING, SCOMMENT> {
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

<QSTRING> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar, StringUtils.APOS_NO_BSESC);
    }
}
