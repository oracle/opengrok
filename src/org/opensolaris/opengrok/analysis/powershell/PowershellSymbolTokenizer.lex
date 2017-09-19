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

package org.opensolaris.opengrok.analysis.powershell;
import java.io.IOException;
import java.io.Reader;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
%%
%public
%class PoshSymbolTokenizer
%extends JFlexTokenizer
%unicode
%init{
super(in);
%init}
%type boolean
%eofval{
return false;
%eofval}
%char

EOL = \r|\n|\r\n
WhiteSpace = [ \t\f]
Identifier = [a-zA-Z_] [a-zA-Z0-9_-]*
SimpleVariable  = [\$] [a-zA-Z_] [a-zA-Z0-9_:-]*
ComplexVariable = [\$] "{" [^}]+  "}"
Label =  {WhiteSpace}* ":" {Identifier}
DataType = "[" [a-zA-Z_] [\[\]a-zA-Z0-9_.-]* "]"

%state STRING COMMENT SCOMMENT QSTRING HERESTRING HEREQSTRING

%%

<YYINITIAL> {
 ^ {Label} {
    String id = yytext();
    setAttribs(id, yychar, yychar + yylength());
    return true;
 } 

 {Identifier} | {SimpleVariable} | {ComplexVariable} {
    String id = yytext();
    if(!Consts.poshkwd.contains(id.toLowerCase())){
        setAttribs(id, yychar, yychar + yylength());
        return true; 
    }
 }
 {DataType} { return true; }

 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 \@\"   { yybegin(HERESTRING); }
 \@\'   { yybegin(HEREQSTRING); }
 "#"    { yybegin(SCOMMENT); }
 "<#"   { yybegin(COMMENT); }
}

<STRING> {
 {SimpleVariable} {
    setAttribs(yytext().substring(1), yychar + 1, yychar + yylength());
    return true;
 }

 {ComplexVariable} {
    int startOffset = 2;            // trim away the "${" prefix
    int endOffset = yylength() - 1; // trim away the "}" suffix
    setAttribs(yytext().substring(startOffset, endOffset),
               yychar + startOffset,
               yychar + endOffset);
    return true;
 }

 \"     { yybegin(YYINITIAL); }
 \\\\ | \\\" | \`\"     {}
}

<HERESTRING> {
 {SimpleVariable} {
    setAttribs(yytext().substring(1), yychar + 1, yychar + yylength());
    return true;
 }

 {ComplexVariable} {
    int startOffset = 2;            // trim away the "${" prefix
    int endOffset = yylength() - 1; // trim away the "}" suffix
    setAttribs(yytext().substring(startOffset, endOffset),
               yychar + startOffset,
               yychar + endOffset);
    return true;
 }

 \"\@     { yybegin(YYINITIAL); }
 [^\r\n]+ {}
}

<HEREQSTRING> {
 \'\@     { yybegin(YYINITIAL); }
 [^\r\n]+ {}
}

<QSTRING> {
 \'      { yybegin(YYINITIAL); }
 \\\\ | \\\' | \`\'     {}
}

<COMMENT> {
 "#>"    { yybegin(YYINITIAL);}
 [^\r\n]+ {}
}

<SCOMMENT> {
 {EOL}   { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING, HERESTRING, HEREQSTRING> {
<<EOF>>   { return false;}
[^]    {}
}
