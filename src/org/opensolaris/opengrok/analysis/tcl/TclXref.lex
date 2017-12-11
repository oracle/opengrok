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
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Tcl file
 */

package org.opensolaris.opengrok.analysis.tcl;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class TclXref
%extends JFlexXrefSimple
%unicode
%int
%include CommonXref.lexh
%{
  private int braceCount;

  @Override
  public void reset() {
      super.reset();
      braceCount = 0;
  }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

File = [a-zA-Z] {FNameChar}+ "." ([a-zA-Z]+)

%state STRING COMMENT SCOMMENT BRACES VARSUB2

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Tcl.lexh
%%
<YYINITIAL>{

 [\{]    {
    out.write(yytext());
    ++braceCount;
    yypush(BRACES);
 }
}

<YYINITIAL, BRACES> {
 {Number}    {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }
 \"     {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 "#"    {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
 }
 {WordOperators}    {
    out.write(htmlize(yytext()));
 }
}

<YYINITIAL, STRING, BRACES> {
    {Backslash_sub}    {
        out.write(htmlize(yytext()));
    }
    {Backslash_nl}    {
        String capture = yytext();
        String esc = capture.substring(0, 1);
        String whsp = capture.substring(1);
        out.write(esc);
        TclUtils.writeWhitespace(this, whsp);
    }
    {Varsub1}    {
        String capture = yytext();
        String sigil = capture.substring(0, 1);
        String name = capture.substring(1);
        out.write(sigil);
        writeSymbol(name, Consts.kwd, yyline);
    }
    {Varsub2}    {
        // TclXref could get away without VARSUB2 as a state, but for ease in
        // comparing to TclSymbolTokenizer, it is modeled here too.
        yypush(VARSUB2);
        String capture = yytext();
        String sigil = capture.substring(0, 1);
        int lparen_i = capture.indexOf("(");
        String name1 = capture.substring(1, lparen_i);
        yypushback(capture.length() - lparen_i - 1);
        out.write(sigil);
        if (name1.length() > 0) {
            writeSymbol(name1, Consts.kwd, yyline);
        }
        out.write("(");
    }
    {Varsub3}    {
        String capture = yytext();
        String sigil = capture.substring(0, 2);
        String name = capture.substring(2, capture.length() - 1);
        String endtoken = capture.substring(capture.length() - 1);
        out.write(sigil);
        writeSymbol(name, Consts.kwd, yyline);
        out.write(endtoken);
    }
}

<VARSUB2> {
    {name_unit}+    {
        String name2 = yytext();
        yypop();
        writeSymbol(name2, Consts.kwd, yyline);
    }
}

<YYINITIAL, BRACES> {
    {OrdinaryWord}    {
        String id = yytext();
        writeSymbol(id, Consts.kwd, yyline);
    }
}

<STRING> {
 \"     {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<BRACES> {
    [\}]    {
        if (--braceCount == 0) {
            yypop();
        }
        out.write(yytext());
    }
    [\{]    {
        ++braceCount;
        out.write(yytext());
    }
}

<SCOMMENT> {
  {WhspChar}*{EOL}    {
    yypop();
    startNewLine();
  }
}

<YYINITIAL, STRING, COMMENT, SCOMMENT, BRACES> {
[&<>\'\"]    { out.write(htmlize(yytext())); }
{WhspChar}*{EOL} { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, SCOMMENT> {
{FPath}
        { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}

{File}
        {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");}

{BrowseableURI}    {
          appendLink(yytext(), true);
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}
