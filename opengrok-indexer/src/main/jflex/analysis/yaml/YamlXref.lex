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
 * Portions Copyright (c) 2023, Gino Augustine <gino.augustine@oracle.com>.
 */

/*
 * Cross reference a Java file
 */

package org.opengrok.indexer.analysis.yaml;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.ScopeAction;
import org.opengrok.indexer.analysis.EmphasisHint;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class YamlXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%include ../Common.lexh
%include ../CommonURI.lexh
%include Yaml.lexh
Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[0-9]+)(([eE][+-]?[0-9]+)?[ufdlUFDL]*)?
KEY_VALUE_START = [-?:]{WhspChar}+
%{
  /* Must match {WhiteSpace} regex */
  private final static String WHITE_SPACE = "[ \\t\\f]+";

  @Override
  public void yypop() throws IOException {
      onDisjointSpanChanged(null, yychar);
      super.yypop();
  }

  protected void chkLOC() {
      switch (yystate()) {
          case SCOMMENT:
              break;
          default:
              phLOC();
              break;
      }
  }
%}


%state STRING SCOMMENT QSTRING KEYVALUE ANCHOR ALIAS
%%
<YYINITIAL>{

 {Identifier}  {
    chkLOC();
    onQueryTermMatched(yytext(), yychar);
 }
 #   {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 {KEY_VALUE_START}  {
    chkLOC();
    yypush(KEYVALUE);
    onNonSymbolMatched(yytext(), yychar);
 }
}
<KEYVALUE>{
 [\&]/{Identifier}    {
            chkLOC();
            yypush(ANCHOR);
            onNonSymbolMatched(yytext(), yychar);
 }
 [*]/{Identifier}    {
            chkLOC();
            yypush(ALIAS);
            onNonSymbolMatched(yytext(), yychar);
 }
 {Number}   {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
 {Identifier}    {
    chkLOC();
    String id = yytext();
    if(Consts.kwd.contains(id)){
       onKeywordMatched(id, yychar);
   }else{
       onNonSymbolMatched(yytext(), yychar);
   }
 }

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
 #   {
    yypop();
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}
<ANCHOR> {
 {Identifier}    {
    chkLOC();
    onSymbolMatched(yytext(), yychar);
    yypop();
 }
}
<ALIAS> {
 {Identifier}    {
     chkLOC();
     onLabelDefMatched(yytext(), yychar);
     yypop();
 }
}
<ANCHOR,ALIAS> {
 [^]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); yypop();}
}
<STRING> {
 \\[\"\\] |
 \" {WhspChar}+ \"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"     {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<QSTRING> {
 \\[\'\\] |
 \' {WhspChar}+ \'    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \'     {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<KEYVALUE,SCOMMENT> {
  {WhspChar}*{EOL} {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<YYINITIAL, STRING, QSTRING> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING,SCOMMENT,KEYVALUE> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar);
    }
}
<QSTRING> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar, StringUtils.APOS_NO_BSESC);
    }
}
