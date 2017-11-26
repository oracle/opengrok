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
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Scala file
 */

package org.opensolaris.opengrok.analysis.scala;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class ScalaXref
%extends JFlexXrefSimple
%unicode
%int
%include CommonXref.lexh
%{
  /* Must match {WhiteSpace} regex */
  private final static String WHITE_SPACE = "[ \\t\\f]+";

  private int nestedComment;

  @Override
  public void reset() {
      super.reset();
      nestedComment = 0;
  }

  private void pushQuotedString(int state, String capture) throws IOException {
      int qoff = capture.indexOf("\"");
      String id = capture.substring(0, qoff);
      String quotes = capture.substring(qoff);
      out.write(id);
      pushSpan(state, HtmlConsts.STRING_CLASS);
      out.write(htmlize(quotes));
  }
%}

File = [a-zA-Z]{FNameChar}* "." ([Ss][Cc][Aa][Ll][Aa] |
    [Pp][Rr][Oo][Pp][Ee][Rr][Tt][Ii][Ee][Ss] | [Pp][Rr][Oo][Pp][Ss] |
    [Xx][Mm][Ll] | [Cc][Oo][Nn][Ff] | [Tt][Xx][Tt] | [Hh][Tt][Mm][Ll]? |
    [Ii][Nn][Ii] | [Jj][Nn][Ll][Pp] | [Jj][Aa][Dd] | [Dd][Ii][Ff][Ff] |
    [Pp][Aa][Tt][Cc][Hh])

JavadocWithClassArg = "@throws" | "@exception"
JavadocWithParamNameArg = "@param"

ClassName = ({Identifier} ".")* {Identifier}
ParamName = {Identifier} | "<" {Identifier} ">"

/*
 * STRING : string literal
 * ISTRING : string literal with interpolation
 * MSTRING : multi-line string literal
 * IMSTRING : multi-line string literal with interpolation
 * QSTRING : character literal
 * SCOMMENT : single-line comment
 * COMMENT : multi-line comment
 * JAVADOC : multi-line comment with JavaDoc conventions
 */
%state STRING ISTRING MSTRING IMSTRING QSTRING SCOMMENT COMMENT JAVADOC

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Scala.lexh
%%
<YYINITIAL>{

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline);
}

 {BacktickIdentifier} {
    String capture = yytext();
    String id = capture.substring(1, capture.length() - 1);
    out.write("`");
    writeSymbol(id, null, yyline);
    out.write("`");
 }

 {OpSuffixIdentifier}    {
    String capture = yytext();
    int uoff = capture.lastIndexOf("_");
    // ctags include the "_" in the symbol, so follow that too.
    String id = capture.substring(0, uoff + 1);
    String rest = capture.substring(uoff + 1);
    writeSymbol(id, Consts.kwd, yyline);
    out.write(htmlize(rest));
 }

"<" ({File}|{FPath}) ">" {
        out.write("&lt;");
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
        out.write("&gt;");
}

/*{Hier}
        { out.write(Util.breadcrumbPath(urlPrefix+"defs=",yytext(),'.'));}
*/
 {Number}        {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }

 ([fs] | "raw") \"    {
    pushQuotedString(ISTRING, yytext());
 }
 {Identifier}? \"    {
    pushQuotedString(STRING, yytext());
 }
 \'     {
    pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 ([fs] | "raw") \"\"\"    {
    pushQuotedString(IMSTRING, yytext());
 }
 {Identifier}? \"\"\"    {
    pushQuotedString(MSTRING, yytext());
 }
 "/*" "*"+ "/"    {
    disjointSpan(HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }
 "/*" "*"+    {
    if (nestedComment++ == 0) {
        pushSpan(JAVADOC, HtmlConsts.COMMENT_CLASS);
    }
    out.write(yytext());
 }
 "//"   {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
 }
}

<STRING, ISTRING> {
 \\[\"\\]    { out.write(htmlize(yytext())); }
 \"     {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<ISTRING, IMSTRING> {
    /*
     * TODO : support "arbitrary expressions" inside curly brackets
     */
    \$ {Identifier}    {
        String capture = yytext();
        String sigil = capture.substring(0, 1);
        String id = capture.substring(1);
        out.write(sigil);
        disjointSpan(null);
        writeSymbol(id, Consts.kwd, yyline);
        disjointSpan(HtmlConsts.STRING_CLASS);
    }
}

<QSTRING> {
 \\[\'\\]    { out.write(htmlize(yytext())); }
 \'     {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<MSTRING, IMSTRING> {
 /*
  * For multi-line string, "Unicode escapes work as everywhere else, but none
  * of the escape sequences [in 'Escape Sequences'] are interpreted."
  */
 \"\"\"    {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<YYINITIAL, COMMENT, JAVADOC> {
    "/*"    {
        if (nestedComment++ == 0) {
            pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
        }
        out.write(yytext());
    }
}

<COMMENT, JAVADOC> {
 "*/"    {
    out.write(yytext());
    if (--nestedComment == 0) {
        yypop();
    }
 }
 {WhspChar}*{EOL}    {
    disjointSpan(null);
    startNewLine();
    disjointSpan(HtmlConsts.COMMENT_CLASS);
 }
}

<JAVADOC> {
  {JavadocWithParamNameArg} {WhiteSpace} {ParamName} |
  {JavadocWithClassArg} {WhiteSpace} {ClassName} {
    String text = yytext();
    String[] tokens = text.split(WHITE_SPACE, 2);
    out.append("<strong>").append(tokens[0]).append("</strong>")
      .append(text.substring(tokens[0].length(),
                             text.length() - tokens[1].length()))
      .append("<em>").append(tokens[1]).append("</em>");
  }
  "@" {Identifier} {
    out.append("<strong>").append(yytext()).append("</strong>");
  }
}

<SCOMMENT> {
  {WhspChar}*{EOL} {
    yypop();
    startNewLine();
  }
}

<YYINITIAL> {
 {OpIdentifier}    {
    out.write(htmlize(yytext()));
 }
}

<YYINITIAL, STRING, ISTRING, MSTRING, IMSTRING, COMMENT, SCOMMENT, QSTRING,
    JAVADOC> {
[&<>\'\"]    { out.write(htmlize(yytext())); }
{WhspChar}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING, MSTRING, COMMENT, SCOMMENT, QSTRING, JAVADOC> {
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

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}

<STRING, MSTRING, QSTRING, SCOMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<ISTRING, IMSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, ScalaUtils.DOLLAR_SIGN);
    }
}

<COMMENT, JAVADOC> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.END_C_COMMENT);
    }
}
