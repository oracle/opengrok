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
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Pascal file
 */

package org.opengrok.indexer.analysis.pascal;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.ScopeAction;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class PascalXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
    protected void chkLOC() {
        switch (yystate()) {
            case COMMENT:
            case PCOMMENT:
            case SCOMMENT:
                break;
            default:
                phLOC();
                break;
        }
    }
%}

File = [a-zA-Z]{FNameChar}* "." ("pas"|"properties"|"props"|"xml"|"conf"|"txt"|"htm"|"html"|"ini"|"diff"|"patch")

%state COMMENT PCOMMENT SCOMMENT QSTRING

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Pascal.lexh
%%
<YYINITIAL>{
 "begin"    { chkLOC(); onScopeChanged(ScopeAction.INC, yytext(), yychar); }
 "end"      { chkLOC(); onScopeChanged(ScopeAction.DEC, yytext(), yychar); }
 \;         { chkLOC(); onScopeChanged(ScopeAction.END, yytext(), yychar); }

\&{Identifier}    {
    chkLOC();
    String id = yytext();
    onNonSymbolMatched("&", yychar);
    onSymbolMatched(id.substring(1), yychar + 1);
}
{Identifier}    {
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

 {Number}        {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 {ControlString}    {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \'     {
    yybegin(QSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \{     {
    yypush(COMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 "(*"    {
    yypush(PCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 "//"   {
    yybegin(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<QSTRING> {
 \'\'    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \'     {
    chkLOC();
    yybegin(YYINITIAL);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
}

<COMMENT> {
 \}      {
    yypop();
    onNonSymbolMatched(yytext(), yychar);
    if (yystate() == YYINITIAL) onDisjointSpanChanged(null, yychar);
 }
 "(*"    {
    yypush(PCOMMENT);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<PCOMMENT> {
 "*)"    {
    yypop();
    onNonSymbolMatched(yytext(), yychar);
    if (yystate() == YYINITIAL) onDisjointSpanChanged(null, yychar);
 }
 \{     {
    yypush(COMMENT);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<SCOMMENT> {
  {WhspChar}*{EOL} {
    yybegin(YYINITIAL);
    onDisjointSpanChanged(null, yychar);
    onEndOfLineMatched(yytext(), yychar);
  }
}


<YYINITIAL, COMMENT, PCOMMENT, SCOMMENT, QSTRING> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<COMMENT, PCOMMENT, SCOMMENT, QSTRING> {
{FPath}
        { chkLOC(); onPathlikeMatched(yytext(), '/', false, yychar); }

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

<COMMENT, SCOMMENT> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar);
    }
}

<PCOMMENT> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar, PascalUtils.END_OLD_PASCAL_COMMENT);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar, PascalUtils.CHARLITERAL_APOS_DELIMITER);
    }
}
