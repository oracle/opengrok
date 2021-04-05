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
 * Gets Kotlin symbols - ignores comments, strings, keywords
 */

// comments can be nested in kotlin, so below logic doesn't allow that with yybegin we save only one nesting
// same for strings

package org.opengrok.indexer.analysis.kotlin;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class KotlinSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%buffer 32766
%int
%include ../CommonLexer.lexh
%char
%{
    private int nestedComment;

    @Override
    public void reset() {
        super.reset();
        nestedComment = 0;
    }
%}

%state STRING COMMENT SCOMMENT QSTRING TSTRING

%include ../Common.lexh
%include Kotlin.lexh
%%

/* TODO : support identifiers escaped by ` `*/
<YYINITIAL> {
{Identifier} {String id = yytext();
                if(!Consts.kwd.contains(id)){
                        onSymbolMatched(id, yychar);
                        return yystate(); }
              }
 {Number}    {}
 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 \"\"\"   { yybegin(TSTRING); }
 "//"   { yybegin(SCOMMENT); }

}

<STRING> {
 \\[\"\$\\]    {}
 \"     { yybegin(YYINITIAL); }
}

<QSTRING> {
 \\[\'\\]    {}
 \'     { yybegin(YYINITIAL); }
}

<TSTRING> {
 /*
  * "raw string ... doesn't support backslash escaping"
  */
  \"\"\"     { yybegin(YYINITIAL); }
}

<STRING, TSTRING> {
    /*
     * TODO : support template expressions inside curly brackets
     */
    \$ {Identifier}    {
        String capture = yytext();
        String sigil = capture.substring(0, 1);
        String id = capture.substring(1);
        if (!Consts.kwd.contains(id)) {
            onSymbolMatched(id, yychar + 1);
            return yystate();
       }
    }
}

<YYINITIAL, COMMENT> {
    "/*"    {
        if (nestedComment++ == 0) {
            yybegin(COMMENT);
        }
    }
}

<COMMENT> {
"*/"    {
    if (--nestedComment == 0) {
        yybegin(YYINITIAL);
    }
 }
}

<SCOMMENT> {
{EOL}      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING, TSTRING> {
{WhspChar}+ |
[^]    {}
}
