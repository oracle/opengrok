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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.powershell;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
import java.util.Stack;
import java.util.regex.Matcher;
%%
%public
%class PoshXref
%extends JFlexXrefSimple
%unicode
%ignorecase
%int
%include CommonXref.lexh
%{
  private final Stack<String> styleStack = new Stack<String>();

  @Override
  protected void clearStack() {
      super.clearStack();
      styleStack.clear();
  }

  private void emitComplexVariable() throws IOException {
    String id = yytext().substring(2, yylength() - 1);
    out.write("${");
    writeSymbol(id, Consts.poshkwd, yyline, false, true);
    out.write("}");
  }

  private void emitSimpleVariable() throws IOException {
    String id = yytext().substring(1);
    out.write("$");
    writeSymbol(id, Consts.poshkwd, yyline, false);
  }

  @Override
  public void pushSpan(int newState, String className) throws IOException {
      super.pushSpan(newState, className);
      styleStack.push(className);
  }

  @Override
  public void yypop() throws IOException {
      super.yypop();
      styleStack.pop();

      if (!styleStack.empty()) {
          String style = styleStack.peek();
          disjointSpan(style);
      }
  }
%}

File = {FNameChar}+ "." ([a-zA-Z0-9]+)

/*
 * Differs from {FPath} in that the path segments are only constrained to be
 * {FNameChar}.
 */
AnyFPath = "/"? {FNameChar}+ ("/" {FNameChar}+)+

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

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Powershell.lexh
%%

<STRING>{
 {ComplexVariable}    {
    emitComplexVariable();
 }
 {SimpleVariable}    {
    emitSimpleVariable();
 }
}

<YYINITIAL>{
 \{     { incScope(); writeUnicodeChar(yycharat(0)); }
 \}     { decScope(); writeUnicodeChar(yycharat(0)); }
 \;     { endScope(); writeUnicodeChar(yycharat(0)); }
}

<YYINITIAL, SUBSHELL> {
 ^ {Label}    {
    out.write("<a class=\"xlbl\" name=\"");
    out.write(yytext().substring(1)); 
    out.write("\">");
    out.write(yytext()); 
    out.write("</a>");
 }
 {Break} |
 {Continue}    {
    String capture = yytext();
    Matcher m = PoshUtils.GOTO_LABEL.matcher(capture);
    if (!m.find()) {
        out.write(htmlize(capture));
    } else {
        String control = m.group(1);
        String space   = m.group(2);
        String label   = m.group(3);
        writeSymbol(control, Consts.poshkwd, yyline, false);
        out.write(space);
        out.write("<a class=\"d intelliWindow-symbol\" href=\"#");
        out.write(label);
        out.write("\" data-definition-place=\"defined-in-file\">");
        out.write(label);
        out.write("</a>");
    }
 }

 {DataType}    {
    yypushback(yylength());
    pushSpan(DATATYPE, null);
 }
}

<YYINITIAL, SUBSHELL, DOTSYNTAX> {
 {ComplexVariable}    {
    emitComplexVariable();
    if (yystate() != DOTSYNTAX) pushSpan(DOTSYNTAX, null);
 }
 {SimpleVariable}    {
    emitSimpleVariable();
    if (yystate() != DOTSYNTAX) pushSpan(DOTSYNTAX, null);
 }
}

<YYINITIAL, SUBSHELL> {
 {Operator}    {
    String capture = yytext();
    if (Consts.poshkwd.contains(capture.toLowerCase())) {
        writeKeyword(capture, yyline);
    } else {
        String sigil = capture.substring(0, 1);
        String id = capture.substring(1);
        out.write(sigil);
        writeSymbol(id, Consts.poshkwd, yyline, false);
    }
 }

 {Number}    {
    String lastClassName = getDisjointSpanClassName();
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(lastClassName);
 }

 \"    {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \'    {
    pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }

 \#    {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
 }
 \<\#    {
    pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(htmlize(yytext()));
 }

 \@\"    {
    pushSpan(HERESTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \@\'    {
    pushSpan(HEREQSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
}

<DOTSYNTAX> {
 "."    {
    out.write(yytext());
 }

 [^]    {
    yypushback(yylength());
    yypop();
 }
}

<YYINITIAL, SUBSHELL, DATATYPE, DOTSYNTAX> {
 {Identifier}    {
    String id = yytext();
    writeSymbol(id, Consts.poshkwd, yyline, false);
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
 \"\"    { out.write(htmlize(yytext())); }

 \$? \"    {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<STRING, HERESTRING> {
 "$("    { pushSpan(SUBSHELL, null); out.write(yytext()); }
}

<QSTRING> {
 \'\'    { out.write(htmlize(yytext())); }
 \'    {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<COMMENT> {
 \#\>    {
    out.write(htmlize(yytext()));
    yypop();
 }
}

<SCOMMENT> {
 {WhspChar}*{EOL}    {
    yypop();
    startNewLine();
 }
}

<SUBSHELL> {
  \)    {
    out.write(yytext());
    yypop();
  }
}

<HERESTRING> {
  // Match escaped dollar sign of variable 
  // (eg. `$var) so it does not turn into web-link.
  "`$"    { out.write(yytext()); }

  {SimpleVariable}    {
     emitSimpleVariable();
  }

  {ComplexVariable}    {
     emitComplexVariable();
  }
  ^ \"\@    {
    out.write(htmlize(yytext()));
    yypop();
  }
}

<HEREQSTRING> {
  ^ "'@"    {
    out.write(yytext());
    yypop();
  }
}

<YYINITIAL, SUBSHELL> {
  /* Don't enter new state if special character is escaped. */
  [`][`\(\)\{\}\"\'\$\#\\]    { out.write(htmlize(yytext())); }

  /* $# should not start a comment. */
  "$#"    { out.write(yytext()); }

  \$ ? \(    { pushSpan(SUBSHELL, null); out.write(yytext()); }
}

<YYINITIAL, SUBSHELL, STRING, SCOMMENT, QSTRING> {
    {File} {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
    }

    {AnyFPath}
            {out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}
}

<YYINITIAL, DATATYPE, SUBSHELL, STRING, COMMENT, SCOMMENT, QSTRING, HERESTRING,
    HEREQSTRING> {
    [&<>\'\"]    { out.write(htmlize(yytext())); }
    {WhspChar}*{EOL}    { startNewLine(); }
    {WhiteSpace}   { out.write(yytext()); }
    [!-~]   { out.write(yycharat(0)); }
    [^\n]   { writeUnicodeChar(yycharat(0)); }
}

<STRING, SCOMMENT, QSTRING> {
{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}

<STRING, SCOMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, PoshUtils.STRINGLITERAL_APOS_DELIMITER);
    }
}

<COMMENT> {
    {BrowseableURI} \>?    {
        appendLink(yytext(), true, PoshUtils.MAYBE_END_MULTILINE_COMMENT);
    }
}
