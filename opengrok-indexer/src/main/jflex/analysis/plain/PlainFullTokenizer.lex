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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.plain;

import java.util.Locale;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class PlainFullTokenizer
%extends JFlexSymbolMatcher
%unicode
%buffer 32766
%int
%include ../CommonLexer.lexh
%caseless
%char

//WhiteSpace     = [ \t\f\r]+|\n
Identifier = [a-zA-Z\p{Letter}_] [a-zA-Z\p{Letter}0-9\p{Number}_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
// No letters in the following, so no toLowerCase(Locale.ROOT) needed.
Printable = [\@\$\%\^\&\-+=\?\.\:!\[\]\{\}*\/\|#\<\>\(\),;~]

%%
{Identifier}|{Number}|{Printable} {
    onSymbolMatched(yytext().toLowerCase(Locale.ROOT), yychar);
    return yystate();
}
[^]    {}
