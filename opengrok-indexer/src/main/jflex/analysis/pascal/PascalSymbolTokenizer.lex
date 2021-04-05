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
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

/*
 * Gets Pascal symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.pascal;

import java.util.Locale;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class PascalSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%include ../CommonLexer.lexh

%state COMMENT PCOMMENT SCOMMENT QSTRING

%include ../Common.lexh
%include Pascal.lexh
%%

<YYINITIAL> {
\&{Identifier}    {
    String id = yytext();
    onSymbolMatched(id.substring(1), yychar + 1);
    return yystate();
}
{Identifier}    {
    String id = yytext();
    if (!Consts.kwd.contains(id.toLowerCase(Locale.ROOT))) {
                        onSymbolMatched(id, yychar);
                        return yystate();
    }
 }
 {Number}    {}
 {ControlString}    {}
 \'     { yybegin(QSTRING); }
 \{     { yypush(COMMENT); }
 "(*"   { yypush(PCOMMENT); }
 "//"   { yybegin(SCOMMENT); }
}

<QSTRING> {
 \'\'    {}
 \'     { yybegin(YYINITIAL); }
}

<COMMENT> {
 \}      { yypop(); }
 "(*"    { yypush(PCOMMENT); }
}

<PCOMMENT> {
 "*)"    { yypop(); }
 \{      { yypush(COMMENT); }
}

<SCOMMENT> {
{WhspChar}*{EOL}    { yybegin(YYINITIAL);}
}

<YYINITIAL, COMMENT, PCOMMENT, SCOMMENT, QSTRING> {
[^]    {}
}
