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
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2023, Gino Augustine <gino.augustine@oracle.com>.
 */

/*
 * Gets YAML symbols - ignores comments, strings, keywords
 */

package org.opengrok.indexer.analysis.yaml;

import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class YamlSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%buffer 32766
%int
%include ../CommonLexer.lexh
%include ../Common.lexh
%include Yaml.lexh
%char

%state STRING QSTRING SCOMMENT ALIAS_ANCHOR

ANCHOR_ALIAS_START = [-?:]{WhspChar}+[*\&]
%%


<YYINITIAL> {

{ANCHOR_ALIAS_START}     { yybegin(ALIAS_ANCHOR); }
 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 "#"   { yybegin(SCOMMENT); }
}

<ALIAS_ANCHOR> {
{Identifier} {
    String id = yytext();
    onSymbolMatched(id, yychar);
    return yystate();
    }
 [^]    {yybegin(YYINITIAL);}
}

<STRING> {
 \\[\"\\]    {}
 \"     { yybegin(YYINITIAL); }
}

<QSTRING> {
 \\[\'\\]    {}
 \'     { yybegin(YYINITIAL); }
}


<SCOMMENT> {
{EOL}    { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, SCOMMENT, QSTRING> {
{WhspChar}+    {}
[^]    {}
}
