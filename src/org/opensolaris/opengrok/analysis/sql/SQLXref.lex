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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis.sql;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class SQLXref
%extends JFlexXref
%unicode
%ignorecase
%int
%{
    private int commentLevel;

    public void reInit(char[] buf, int len) {
        yyreset((Reader) null);
        zzBuffer = buf;
        zzEndRead = len;
        zzAtEOF = true;
        zzStartRead = 0;
        annotation = null;
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

Whitespace = [ \t\f]+
EOL = \r|\n|\r\n

%state STRING QUOTED_IDENTIFIER SINGLE_LINE_COMMENT BRACKETED_COMMENT

%%

<YYINITIAL> {
    {Identifier} {
        String id = yytext();
        writeSymbol(id, Consts.getReservedKeywords(), yyline);
    }

    {Number} {
        out.append("<span class=\"n\">").append(yytext()).append("</span>");
    }

    "'" { yybegin(STRING); out.append("<span class=\"s\">'"); }

    \" { yybegin(QUOTED_IDENTIFIER); out.append("<span class=\"s\">\""); }

    "--" { yybegin(SINGLE_LINE_COMMENT); out.append("<span class=\"c\">--"); }

    "/*" {
        yybegin(BRACKETED_COMMENT);
        commentLevel = 1;
        out.append("<span class=\"c\">/*");
    }
}

<STRING> {
    "''" { out.append("''"); }
    "'"   { yybegin(YYINITIAL); out.append("'</span>"); }
}

<QUOTED_IDENTIFIER> {
    \"\" { out.append("\"\""); }
    \"   { yybegin(YYINITIAL); out.append("\"</span>"); }
}

<SINGLE_LINE_COMMENT> {
    {EOL} {
        yybegin(YYINITIAL);
        out.append("</span>");
        startNewLine();
    }
}

<BRACKETED_COMMENT> {
    "/*" { out.append(yytext()); commentLevel++; }
    "*/" {
        commentLevel--;
        out.append(yytext());
        if (commentLevel == 0) {
            yybegin(YYINITIAL);
            out.append("</span>");
        }
    }
}

<YYINITIAL, STRING, QUOTED_IDENTIFIER, SINGLE_LINE_COMMENT, BRACKETED_COMMENT> {
    "&"    { out.append( "&amp;"); }
    "<"    { out.append( "&lt;"); }
    ">"    { out.append( "&gt;"); }
    {EOL}     { startNewLine(); }
    {Whitespace}  { out.append(yytext()); }
    [ \t\f\r!-~]  { out.append(yycharat(0)); }
    .      { writeUnicodeChar(yycharat(0)); }
}