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
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.sql;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class SQLXref
%extends JFlexXrefSimple
%unicode
%ignorecase
%int
%include CommonXref.lexh
%{
    private int commentLevel;

    @Override
    public void reset() {
        super.reset();
        commentLevel = 0;
    }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

Sign = "+" | "-"
SimpleNumber = [0-9]+ | [0-9]+ "." [0-9]* | [0-9]* "." [0-9]+
ScientificNumber = ({SimpleNumber} [eE] {Sign}? [0-9]+)

Number = {Sign}? ({SimpleNumber} | {ScientificNumber})

Identifier = [a-zA-Z] [a-zA-Z0-9_]*

%state STRING QUOTED_IDENTIFIER SINGLE_LINE_COMMENT BRACKETED_COMMENT

%include Common.lexh
%include CommonURI.lexh
%%

<YYINITIAL> {
    {Identifier} {
        String id = yytext();
        writeSymbol(id, Consts.getReservedKeywords(), yyline);
    }

    {Number} {
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        out.write(yytext());
        disjointSpan(null);
    }

    "'"    {
        pushSpan(STRING, HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
    }

    \"    {
        pushSpan(QUOTED_IDENTIFIER, HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
    }

    "--"    {
        pushSpan(SINGLE_LINE_COMMENT, HtmlConsts.COMMENT_CLASS);
        out.write(yytext());
    }
}

<STRING> {
    "''"    { out.write(htmlize(yytext())); }
    "'"    {
        out.write(htmlize(yytext()));
        yypop();
    }
}

<QUOTED_IDENTIFIER> {
    \"\"    { out.write(htmlize(yytext())); }
    \"    {
        out.write(htmlize(yytext()));
        yypop();
    }
}

<SINGLE_LINE_COMMENT> {
    {WhspChar}*{EOL} {
        yypop();
        startNewLine();
    }
}

<YYINITIAL, BRACKETED_COMMENT> {
    "/*" {
        if (commentLevel++ == 0) {
            pushSpan(BRACKETED_COMMENT, HtmlConsts.COMMENT_CLASS);
        }
        out.write(yytext());
    }
}

<BRACKETED_COMMENT> {
    "*/" {
        out.write(yytext());
        if (--commentLevel == 0) {
            yypop();
        }
    }
}

<YYINITIAL, STRING, QUOTED_IDENTIFIER, SINGLE_LINE_COMMENT, BRACKETED_COMMENT> {
    [&<>\'\"]    { out.write(htmlize(yytext())); }
    {WhspChar}*{EOL}    { startNewLine(); }
    {WhiteSpace}  { out.append(yytext()); }
    [ \t\f\r!-~]  { out.append(yycharat(0)); }
    [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, SQLUtils.STRINGLITERAL_APOS_DELIMITER);
    }
}

<QUOTED_IDENTIFIER, SINGLE_LINE_COMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<BRACKETED_COMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.END_C_COMMENT);
    }
}
