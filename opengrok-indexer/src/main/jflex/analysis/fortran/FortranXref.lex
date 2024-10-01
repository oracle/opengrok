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
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Fortran file
 */
package org.opengrok.indexer.analysis.fortran;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class FortranXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
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
            case SCOMMENT:
            case LCOMMENT:
                break;
            default:
                phLOC();
                break;
        }
    }
%}

File = [a-zA-Z]{FNameChar}* "." ("i"|"inc")

%state  STRING SCOMMENT QSTRING LCOMMENT

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Fortran.lexh
%%
<YYINITIAL>{
 ^{Label} {
    chkLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }
 ^[^ \t\f\r\n]+ {
    yypush(LCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
}

{Identifier} {
    chkLOC();
    onFilteredSymbolMatched(yytext(), yychar, Consts.kwd, false);
}

"'" ({File}|{FPath}) "'" |
"<" ({File}|{FPath}) ">" {
    chkLOC();
    String cmatch = yytext();
    onNonSymbolMatched(cmatch, yychar);
    String file = cmatch.substring(1, cmatch.length() - 1);
    onFilelikeMatched(file, yychar + 1);
    onNonSymbolMatched(cmatch, yychar + 1 + file.length());
}

/*{Hier}
        { onPathlikeMatched(yytext(), '.', false, yychar); }
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
 \!     {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \"\"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"     {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<QSTRING> {
 \'\'    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \'     {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<STRING, QSTRING> {
    {WhspChar}*{EOL}    {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    }
}

<SCOMMENT, LCOMMENT> {
    {WhspChar}*{EOL}    {
        yypop();
        onEndOfLineMatched(yytext(), yychar);
    }
}

<YYINITIAL, STRING, SCOMMENT, QSTRING, LCOMMENT> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<SCOMMENT, STRING, QSTRING> {
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

<SCOMMENT, STRING> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar, FortranUtils.CHARLITERAL_APOS_DELIMITER);
    }
}
