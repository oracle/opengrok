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
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Python file
 */

package org.opensolaris.opengrok.analysis.python;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class PythonXref
%extends JFlexSymbolMatcher
%unicode
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

File = [a-zA-Z]{FNameChar}* "." ([Pp][Yy] | [Pp][Mm] | [Cc][Oo][Nn][Ff] |
    [Tt][Xx][Tt] | [Hh][Tt][Mm][Ll]? | [Xx][Mm][Ll] | [Ii][Nn][Ii] |
    [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state  STRING LSTRING SCOMMENT QSTRING LQSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Python.lexh
%%
<YYINITIAL>{

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

 {Number}    {
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \"     {
    yypush(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \"\"\" {
    yypush(LSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \'     {
    yypush(QSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \'\'\' {
    yypush(LQSTRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 "#"   {
    yypush(SCOMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \\[\"\\]    { onNonSymbolMatched(yytext(), yychar); }
 \"     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
 {WhspChar}*{EOL} {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
}

<QSTRING> {
 \\[\'\\]    { onNonSymbolMatched(yytext(), yychar); }
 \'     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
 {WhspChar}*{EOL} {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
}

<LSTRING> {
 \\[\"\\]    { onNonSymbolMatched(yytext(), yychar);}
 \"\"\" {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<LQSTRING> {
 \\[\'\\]    { onNonSymbolMatched(yytext(), yychar); }
 \'\'\'     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<SCOMMENT> {
  {WhspChar}*{EOL} {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
}

<YYINITIAL, STRING, SCOMMENT, QSTRING , LSTRING, LQSTRING> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [^\n]    { onNonSymbolMatched(yytext(), yychar); }
}

<STRING, SCOMMENT, STRING, QSTRING , LSTRING, LQSTRING> {
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

<SCOMMENT, STRING, LSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, StringUtils.APOS_NO_BSESC);
    }
}

<LQSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, PythonUtils.LONGSTRING_APOS);
    }
}
