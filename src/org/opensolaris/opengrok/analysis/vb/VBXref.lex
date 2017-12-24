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
 * Cross reference a VB file
 */

package org.opensolaris.opengrok.analysis.vb;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class VBXref
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

File = [a-zA-Z]{FNameChar}* "." ("vb"|"cls"|"frm"|"vbs"|"bas"|"ctl")

%state  STRING COMMENT

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include VB.lexh
%%
<YYINITIAL>{

{Identifier} {
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.reservedKeywords, false);
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

 \"   {
    yypush(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
 \'   {
    yypush(COMMENT);
    onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \"\" |
 \" {WhspChar}+ \"    { onNonSymbolMatched(yytext(), yychar); }
 \"     {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
 {WhspChar}*{EOL}    {
    onDisjointSpanChanged(null, yychar);
    onEndOfLineMatched(yytext(), yychar);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
 }
}

<COMMENT> {
  {WhspChar}*{EOL} {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
  }
}


<YYINITIAL, STRING, COMMENT> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [^\n]    { onNonSymbolMatched(yytext(), yychar); }
}

<STRING, COMMENT> {
{FPath}
        { onPathlikeMatched(yytext(), '/', false, yychar); }

{File}
        {
        String path = yytext();
        onFilelikeMatched(path, yychar);
 }

{BrowseableURI}    {
          onUriMatched(yytext(), yychar);
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          onEmailAddressMatched(yytext(), yychar);
        }
}
