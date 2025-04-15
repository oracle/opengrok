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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2025, Yelisey Romanov <progoramur@gmail.com>.
 */

/*
 * Get OCaml symbols
 */

package org.opengrok.indexer.analysis.ocaml;

import java.io.IOException;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;

/**
 * @author Harry Pan
 */
%%
%public
%class OCamlSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char
%{
    private int nestedComment;
    private String quotedStringKey;

    public void reset() {
        super.reset();
        nestedComment = 0;
        quotedStringKey = "";
    }
%}

%state STRING QSTRING QEXTENSIONBEGIN BCOMMENT

%include ../Common.lexh
%include OCaml.lexh
%%

<YYINITIAL> {
    {Character} {}
    {Identifier} {
        String id = yytext();
        if (!Consts.kwd.contains(id)) {
            onSymbolMatched(id, yychar);
            return yystate();
        }
    }
    {Extension} {}
    {Number}    {}
    \"   { yybegin(STRING);   }
    {QuotedStringBegin} {
        String key = yytext();
        quotedStringKey = key.substring(1, key.length() - 1);
        yybegin(QSTRING);
    }
    {QuotedExtensionBegin}     {
        yypush(QEXTENSIONBEGIN);
    }
}

<STRING> {
    \\[\"\\]    {}
    \"   { yybegin(YYINITIAL); }
}

<QSTRING> {
    {QuotedStringEnd} {
        String key = yytext();
        if (quotedStringKey.equals(
              key.substring(1, key.length() - 1))) {
            quotedStringKey = "";
            yybegin(YYINITIAL);
        }
    }
}

<QEXTENSIONBEGIN> {
    {QuotedExtensionKey}         {
        String key = yytext();
        quotedStringKey = key.substring(0, key.length() - 1);
        yybegin(QSTRING);
    }
}

<YYINITIAL, BCOMMENT> {
    "(*"    {
        if (nestedComment++ == 0) {
            yybegin(BCOMMENT);
        }
    }
}

<BCOMMENT> {
    "*)"    {
        if (--nestedComment == 0) {
            yybegin(YYINITIAL);
        }
    }
}

// fallback
{WhspChar}+ |
[^] {}
