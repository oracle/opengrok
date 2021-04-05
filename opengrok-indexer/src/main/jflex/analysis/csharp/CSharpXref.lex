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
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a C# file
 * @author Christoph Hofmann - ChristophHofmann AT gmx dot de
 */

package org.opengrok.indexer.analysis.csharp;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.ScopeAction;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class CSharpXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
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

File = [a-zA-Z]{FNameChar}* "." ([cChHtTsS]|[cC][sS])

%state  STRING COMMENT SCOMMENT QSTRING VSTRING

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include CSharp.lexh
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

"<" ({File} | {FPath}) ">" {
        chkLOC();
        onNonSymbolMatched("<", yychar);
        String path = yytext().substring(1, yylength() - 1);
        onFilelikeMatched(path, yychar + 1);
        onNonSymbolMatched(">", yychar + 1 + path.length());
}

/*{Hier}
    { onPathlikeMatched(yytext(), '.', false, yychar);}
*/
{Number}        {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
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
 "@\""  {
    chkLOC();
    yypush(VSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
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

<VSTRING> {
 \\ |
 \"\"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"       {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<COMMENT> {
"*/"    {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<SCOMMENT> {
  {WhspChar}*{CsharpEOL} {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING, VSTRING> {
{WhspChar}*{CsharpEOL}    { onEndOfLineMatched(yytext(), yychar); }
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING, COMMENT, SCOMMENT, QSTRING, VSTRING> {
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

<STRING, SCOMMENT, VSTRING> {
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
