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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Get Golang symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.golang;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;

/**
 * @author Patrick Lundquist
 */
%%
%public
%class GolangSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%char

%state STRING COMMENT SCOMMENT QSTRING

%include ../Common.lexh
%include Golang.lexh
%%

<YYINITIAL> {
    {Identifier} {
        String id = yytext();
        if (!Consts.kwd.contains(id)) {
            onSymbolMatched(id, yychar);
            return yystate();
        }
    }
    {Number}    {}
    \"   { yybegin(STRING);   }
    \'   { yybegin(QSTRING);  }
    "/*" { yybegin(COMMENT);  }
    "//" { yybegin(SCOMMENT); }
}

<STRING> {
    \\[\"\\]    {}
    \"    { yybegin(YYINITIAL); }
}

<QSTRING> {
    \\[\'\\]    {}
    \'    { yybegin(YYINITIAL); }
}

<COMMENT> {
    "*/" { yybegin(YYINITIAL); }
}

<SCOMMENT> {
    {EOL} { yybegin(YYINITIAL); }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
{WhspChar}+    {}
[^] {}
}
