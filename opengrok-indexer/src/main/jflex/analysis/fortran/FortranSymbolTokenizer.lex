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
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.fortran;

import java.util.Locale;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class FortranSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%include ../CommonLexer.lexh
%char

// (OK to exclude LCOMMENT state used in FortranXref.)
%state STRING SCOMMENT QSTRING

%include ../Common.lexh
%include Fortran.lexh
%%

<YYINITIAL> {
 ^{Label} { }
 ^[^ \t\f\r\n]+ { yybegin(SCOMMENT); }
{Identifier} {String id = yytext();
    if (!Consts.kwd.contains(id.toLowerCase(Locale.ROOT))) {
                        onSymbolMatched(id, yychar);
                        return yystate(); }
              }

 {Number}        {}

 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 \!     { yybegin(SCOMMENT); }
}

<STRING> {
 \"\"    {}
 \"     { yybegin(YYINITIAL); }
}

<QSTRING> {
 \'\'    {}
 \'     { yybegin(YYINITIAL); }
}

<SCOMMENT> {
{WhspChar}+    {}
{EOL}    { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, SCOMMENT, QSTRING> {
[^]    {}
}
