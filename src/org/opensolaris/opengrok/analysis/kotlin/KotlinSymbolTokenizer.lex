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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Gets Java symbols - ignores comments, strings, keywords
 */

// comments can be nested in kotlin, so below logic doesn't allow that with yybegin we save only one nesting
// same for strings

package org.opensolaris.opengrok.analysis.kotlin;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;

%%
%public
%class KotlinSymbolTokenizer
%extends JFlexTokenizer
%init{
super(in);
%init}
%unicode
%buffer 32766
%type boolean
%eofval{
this.finalOffset =  zzEndRead;
return false;
%eofval}
%char

Identifier = [:jletter:] [:jletterdigit:]*

%state STRING COMMENT SCOMMENT QSTRING TSTRING

%%

/* TODO : support identifiers escaped by ` `*/
<YYINITIAL> {
{Identifier} {String id = yytext();
                if(!Consts.kwd.contains(id)){
                        setAttribs(id, yychar, yychar + yylength());
                        return true; }
              }

 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 \"\"\"   { yybegin(TSTRING); }
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

<TSTRING> {
  \"\"\"     { yybegin(YYINITIAL); }
}

<COMMENT> {
"*/"    { yybegin(YYINITIAL);}
}

<SCOMMENT> {
\n      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING, TSTRING> {
<<EOF>>   { this.finalOffset =  zzEndRead; return false;}
[^]    {}
}
