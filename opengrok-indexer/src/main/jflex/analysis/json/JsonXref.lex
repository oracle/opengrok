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
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Json file
 */

package org.opengrok.indexer.analysis.json;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import java.io.IOException;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class JsonXref
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
%}

File = [a-zA-Z]{FNameChar}* "." ([Jj][Ss] |
    [Pp][Rr][Oo][Pp][Ee][Rr][Tt][Ii][Ee][Ss] | [Pp][Rr][Oo][Pp][Ss] |
    [Xx][Mm][Ll] | [Cc][Oo][Nn][Ff] | [Tt][Xx][Tt] | [Hh][Tt][Mm] |
    [Hh][Tt][Mm][Ll]? | [Ii][Nn][Ii] | [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state  STRING

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Json.lexh
%%
<YYINITIAL>{


//TODO add support for identifiers on the left side, restruct the way how we see quoted strings, ctags detect them as identifiers, xref should print them that way too
//improve per http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf
{Identifier} {
    phLOC();
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.kwd);
}

"<" ({File}|{FPath}) ">" {
        phLOC();
        onNonSymbolMatched("<", yychar);
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        onFilelikeMatched(path, yychar + 1);
        onNonSymbolMatched(">", yychar + 1 + path.length());
}

/*
        { onPathlikeMatched(yytext(), '.', false, yychar); }
*/

{Number}        {
    phLOC();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar);
 }

 \"     {
    phLOC();
    yypush(STRING);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
  }
}

<STRING> {
 \\[\"\\] |
 \" {WhspChar}+ \"    { phLOC(); onNonSymbolMatched(yytext(), yychar); }
 \"     {
    phLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<YYINITIAL, STRING> {
{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
 [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
 [^\n]    { phLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING> {
 {FPath}    {
    phLOC();
    onPathlikeMatched(yytext(), '/', false, yychar);
 }

{File}
        {
        phLOC();
        String path = yytext();
        onFilelikeMatched(path, yychar);
 }

{BrowseableURI}    {
          phLOC();
          onUriMatched(yytext(), yychar);
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          phLOC();
          onEmailAddressMatched(yytext(), yychar);
        }
}
