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
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Fortran file
 */
package org.opensolaris.opengrok.analysis.fortran;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class FortranXref
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
    @Override
    public void yypop() throws IOException {
        onDisjointSpanChanged(null, yychar);
        super.yypop();
    }
%}

File = [a-zA-Z]{FNameChar}* ".inc"

%state  STRING SCOMMENT QSTRING LCOMMENT

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Fortran.lexh
%%
<YYINITIAL>{
 ^{Label} {
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
    String id = yytext();
    // For historical reasons, FortranXref doesn't link identifiers of length=1
    if (id.length() > 1) {
        onFilteredSymbolMatched(id, yychar, Consts.kwd, false);
    } else {
        onNonSymbolMatched(id, yychar);
    }
}

"<" ({File}|{FPath}) ">" {
    onNonSymbolMatched("<", yychar);
    String file = yytext();
    file = file.substring(1, file.length() - 1);
    onFilelikeMatched(file, yychar + 1);
    onNonSymbolMatched(">", yychar + 1 + file.length());
}

/*{Hier}
        { onPathlikeMatched(yytext(), '.', false, yychar); }
*/
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
 \!     {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \"\"    { onNonSymbolMatched(yytext(), yychar); }
 \"     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<QSTRING> {
 \'\'    { onNonSymbolMatched(yytext(), yychar); }
 \'     {
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
 [^\n]      { onNonSymbolMatched(yytext(), yychar); }
}

<SCOMMENT, STRING, QSTRING> {
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

<SCOMMENT, STRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, FortranUtils.CHARLITERAL_APOS_DELIMITER);
    }
}
