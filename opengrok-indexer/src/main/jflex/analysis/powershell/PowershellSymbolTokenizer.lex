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
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.powershell;

import java.util.Locale;
import java.util.regex.Matcher;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class PoshSymbolTokenizer
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%include ../CommonLexer.lexh
%char
%{
    private boolean onCertainlyPublish(String symbol, int yyoffset) {
        return onPossiblyPublish(symbol, yyoffset, true);
    }

    private boolean onPossiblyPublish(String symbol, int yyoffset) {
        return onPossiblyPublish(symbol, yyoffset, false);
    }

    private boolean onPossiblyPublish(String symbol, int yyoffset,
        boolean skipKeywordCheck) {
        if (skipKeywordCheck || !Consts.poshkwd.contains(symbol.
                toLowerCase(Locale.ROOT))) {
            onSymbolMatched(symbol, yychar + yyoffset);
            return true;
        }
        return false;
    }
%}

/*
 * States:
 * STRING   - double-quoted string, ex: "hello, world!"
 * QSTRING  - single-quoted string, ex: 'hello, world!'
 * COMMENT - multiple-line comment.
 * SCOMMENT - single-line comment, ex: # this is a comment
 * SUBSHELL - commands executed in a sub-shell,
 *               example 1: (echo $header; cat file.txt)
 * HERESTRING  - here-string, example: cat @" ... "@
 * HEREQSTRING - here-string, example: cat @' ... '@
 * DATATYPE - bracketed .NET datatype specification
 * DOTSYNTAX - await possible dot syntax -- e.g. property or methods
 */
%state STRING COMMENT SCOMMENT QSTRING SUBSHELL HERESTRING HEREQSTRING
%state DATATYPE DOTSYNTAX

%include ../Common.lexh
%include Powershell.lexh
%%

<STRING> {
 {ComplexVariable}    {
    int startOffset = 2;            // trim away the "${" prefix
    int endOffset = yylength() - 1; // trim away the "}" suffix
    String id = yytext().substring(startOffset, endOffset);
    if (onPossiblyPublish(id, startOffset)) return yystate();
 }
 {SimpleVariable}    {
    int startOffset = 1;	// trim away the "$" prefix
    String id = yytext().substring(startOffset);
    if (onPossiblyPublish(id, startOffset)) return yystate();
 }
}

<YYINITIAL, SUBSHELL> {
 ^ {Label}    {
    String id = yytext();
    if (onPossiblyPublish(id, 0)) return yystate();
 }
 {Break} |
 {Continue}    {
    String capture = yytext();
    Matcher m = PoshUtils.GOTO_LABEL.matcher(capture);
    if (m.find()) {
        String label   = m.group(3);
        onCertainlyPublish(label, m.start(3));
        return yystate();
    }
 }

 {DataType}    {
    yypushback(yylength());
    yypush(DATATYPE);
 }
}

<YYINITIAL, SUBSHELL, DOTSYNTAX> {
 {ComplexVariable}    {
    int startOffset = 2;	// trim away the "${" prefix
    String id = yytext().substring(startOffset, yylength() - 1);
    if (onPossiblyPublish(id, startOffset)) return yystate();
    if (yystate() != DOTSYNTAX) yypush(DOTSYNTAX);
 }
 {SimpleVariable}    {
    int startOffset = 1;	// trim away the "$" prefix
    String id = yytext().substring(startOffset);
    if (onPossiblyPublish(id, startOffset)) return yystate();
    if (yystate() != DOTSYNTAX) yypush(DOTSYNTAX);
 }
}

<YYINITIAL, SUBSHELL> {
 {Operator}    {
    String capture = yytext();
    int startOffset = 1;	// trim away the "-" prefix
    String id = capture.substring(startOffset);
    if (!Consts.poshkwd.contains(capture.toLowerCase(Locale.ROOT)) &&
            onPossiblyPublish(id, startOffset)) {
        return yystate(); 
    }
 }

 {Number}    {}

 \"     { yypush(STRING); }
 \'     { yypush(QSTRING); }
 "#"    { yypush(SCOMMENT); }
 "<#"   { yypush(COMMENT); }
 \@\"   { yypush(HERESTRING); }
 \@\'   { yypush(HEREQSTRING); }
}

<DOTSYNTAX> {
 "."    {
    // noop
 }

 [^]    {
    yypushback(yylength());
    yypop();
 }
}

<YYINITIAL, SUBSHELL, DATATYPE, DOTSYNTAX> {
 {Identifier}    {
    String id = yytext();
    if (onPossiblyPublish(id, 0)) return yystate();
 }
}

<DATATYPE> {
 "]"    {
    yypushback(yylength());
    yypop();
 }
}

<STRING> {
 [`][\"\$`] |
 \"\"    {}

 \$? \"     { yypop(); }
}

<STRING, HERESTRING> {
 "$("    { yypush(SUBSHELL); }
}

<QSTRING> {
 \'\'    {}
 \'      { yypop(); }
}

<COMMENT> {
 "#>"    { yypop();}
}

<SCOMMENT> {
 {EOL}   { yypop();}
}

<SUBSHELL> {
  \)    { yypop(); }
}

<HERESTRING> {
  "`$"    {}

 {SimpleVariable}    {
    int startOffset = 1;	// trim away the "$" prefix
    String id = yytext().substring(startOffset);
    if (onPossiblyPublish(id, startOffset)) return yystate();
 }

 {ComplexVariable}    {
    int startOffset = 2;            // trim away the "${" prefix
    int endOffset = yylength() - 1; // trim away the "}" suffix
    String id = yytext().substring(startOffset, endOffset);
    if (onPossiblyPublish(id, startOffset)) return yystate();
 }

 ^ \"\@     { yypop(); }
}

<HEREQSTRING> {
 ^ "'@"     { yypop(); }
}

<YYINITIAL, SUBSHELL> {
  /* Don't enter new state if special character is escaped. */
  [`][`\(\)\{\}\"\'\$\#\\]    {}

  /* $# should not start a comment. */
  "$#"    {}

  \$ ? \(    { yypush(SUBSHELL); }
}

<YYINITIAL, DATATYPE, SUBSHELL, STRING, COMMENT, SCOMMENT, QSTRING, HERESTRING,
    HEREQSTRING> {
{WhspChar}+ |
[^]    {}
}
