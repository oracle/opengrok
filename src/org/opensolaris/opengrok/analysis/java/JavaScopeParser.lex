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
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Parses Java scopes - ignores comments, strings, keywords
 */

package org.opensolaris.opengrok.analysis.java;
import org.opensolaris.opengrok.analysis.JFlexScopeParser;

%%
%public
%class JavaScopeParser
%extends JFlexScopeParser
%unicode
%line
%column
%int
%{
  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
  @Override
  protected void start(String defText) { level = 0; }
  
  private int level = 0;
  private void incScope() { level++; }
  private void decScope() { if (level > 0) { level--; if (level == 0) { scope.lineTo = yyline; } } }
  private void endScope() { if (level == 0) { scope.lineTo = yyline; } }
%}

%state STRING COMMENT SCOMMENT QSTRING

%%

<YYINITIAL> {

 \{     { incScope(); }
 \}     { decScope(); if (level == 0) { return yyeof; } }
 \;     { endScope(); if (level == 0) { return yyeof; } }

 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 "/*"   { yybegin(COMMENT); }
 "//"   { yybegin(SCOMMENT); }
}

<STRING> {
 \"     { yybegin(YYINITIAL); }
\\\\ | \\\"     {}
}

<QSTRING> {
 \'     { yybegin(YYINITIAL); }
}

<COMMENT> {
"*/"    { yybegin(YYINITIAL);}
}

<SCOMMENT> {
\n      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
<<EOF>>   { return yyeof;}
[^]    {}
}
