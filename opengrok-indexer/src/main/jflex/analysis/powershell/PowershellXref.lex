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

import java.io.IOException;
import java.util.Locale;
import java.util.Stack;
import java.util.regex.Matcher;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.ScopeAction;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class PoshXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
  private final Stack<String> styleStack = new Stack<String>();

  @Override
  protected void clearStack() {
      super.clearStack();
      styleStack.clear();
  }

  private void emitComplexVariable() throws IOException {
    String id = yytext().substring(2, yylength() - 1);
    onNonSymbolMatched("${", yychar);
    onFilteredSymbolMatched(id, yychar, Consts.poshkwd, false);
    onNonSymbolMatched("}", yychar);
  }

  private void emitSimpleVariable() throws IOException {
    String id = yytext().substring(1);
    onNonSymbolMatched("$", yychar);
    onFilteredSymbolMatched(id, yychar, Consts.poshkwd, false);
  }

  public void pushSpan(int newState, String className) throws IOException {
      onDisjointSpanChanged(className, yychar);
      yypush(newState);
      styleStack.push(className);
  }

  @Override
  public void yypop() throws IOException {
      onDisjointSpanChanged(null, yychar);
      super.yypop();
      styleStack.pop();

      if (!styleStack.empty()) {
          String style = styleStack.peek();
          onDisjointSpanChanged(style, yychar);
      }
  }

  protected void chkLOC() {
      switch (yystate()) {
          case COMMENT:
          case SCOMMENT:
              break;
          default:
              phLOC();
              break;
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

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%include Powershell.lexh
%%

<STRING>{
 {ComplexVariable}    {
    chkLOC();
    emitComplexVariable();
 }
 {SimpleVariable}    {
    chkLOC();
    emitSimpleVariable();
 }
}

<YYINITIAL>{
 \{     { chkLOC(); onScopeChanged(ScopeAction.INC, yytext(), yychar); }
 \}     { chkLOC(); onScopeChanged(ScopeAction.DEC, yytext(), yychar); }
 \;     { chkLOC(); onScopeChanged(ScopeAction.END, yytext(), yychar); }
}

<YYINITIAL, SUBSHELL> {
 ^ {Label}    {
    chkLOC();
    String capture = yytext();
    onLabelMatched(capture, yychar, capture.substring(1));
 }
 {Break} |
 {Continue}    {
    chkLOC();
    String capture = yytext();
    Matcher m = PoshUtils.GOTO_LABEL.matcher(capture);
    if (!m.find()) {
        onNonSymbolMatched(capture, yychar);
    } else {
        String control = m.group(1);
        String space   = m.group(2);
        String label   = m.group(3);
        onFilteredSymbolMatched(control, yychar, Consts.poshkwd, false);
        onNonSymbolMatched(space, yychar);
        onLabelDefMatched(label, yychar);
    }
 }

 {DataType}    {
    chkLOC();
    yypushback(yylength());
    pushSpan(DATATYPE, null);
 }
}

<YYINITIAL, SUBSHELL, DOTSYNTAX> {
 {ComplexVariable}    {
    chkLOC();
    emitComplexVariable();
    if (yystate() != DOTSYNTAX) pushSpan(DOTSYNTAX, null);
 }
 {SimpleVariable}    {
    chkLOC();
    emitSimpleVariable();
    if (yystate() != DOTSYNTAX) pushSpan(DOTSYNTAX, null);
 }
}

<YYINITIAL, SUBSHELL> {
 {Operator}    {
    chkLOC();
    String capture = yytext();
    if (Consts.poshkwd.contains(capture.toLowerCase(Locale.ROOT))) {
        onKeywordMatched(capture, yychar);
    } else {
        String sigil = capture.substring(0, 1);
        String id = capture.substring(1);
        onNonSymbolMatched(sigil, yychar);
        onFilteredSymbolMatched(id, yychar, Consts.poshkwd, false);
    }
 }

 {Number}    {
    chkLOC();
    String lastClassName = getDisjointSpanClassName();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(lastClassName, yychar);
 }

 \"    {
    chkLOC();
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
 \'    {
    chkLOC();
    pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }

 \#    {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
 \<\#    {
    pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }

 \@\"    {
    chkLOC();
    pushSpan(HERESTRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
 \@\'    {
    chkLOC();
    pushSpan(HEREQSTRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<DOTSYNTAX> {
 "."    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
 }

 [^]    {
    yypushback(yylength());
    yypop();
 }
}

<YYINITIAL, SUBSHELL, DATATYPE, DOTSYNTAX> {
 {Identifier}    {
    chkLOC();
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.poshkwd, false);
 }
}

<DATATYPE> {
 "]"    {
    chkLOC();
    yypushback(yylength());
    yypop();
 }
}

<STRING> {
 [`][\"\$`] |
 \"\"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }

 \$? \"    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<STRING, HERESTRING> {
 "$("    {
    chkLOC();
    pushSpan(SUBSHELL, null);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<QSTRING> {
 \'\'    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
 \'    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<COMMENT> {
 \#\>    {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<SCOMMENT> {
 {WhspChar}*{EOL}    {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
 }
}

<SUBSHELL> {
  \)    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
  }
}

<HERESTRING> {
  // Match escaped dollar sign of variable 
  // (eg. `$var) so it does not turn into web-link.
  "`$"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }

  {SimpleVariable}    {
     chkLOC();
     emitSimpleVariable();
  }

  {ComplexVariable}    {
     chkLOC();
     emitComplexVariable();
  }
  ^ \"\@    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
  }
}

<HEREQSTRING> {
  ^ "'@"    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    yypop();
  }
}

<YYINITIAL, SUBSHELL> {
  /* Don't enter new state if special character is escaped. */
  [`][`\(\)\{\}\"\'\$\#\\]    {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
  }

  /* $# should not start a comment. */
  "$#"    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }

  \$ ? \(    {
    chkLOC();
    pushSpan(SUBSHELL, null);
    onNonSymbolMatched(yytext(), yychar);
  }
}

<YYINITIAL, SUBSHELL, STRING, SCOMMENT, QSTRING> {
    {File} {
        chkLOC();
        String path = yytext();
        onFilelikeMatched(path, yychar);
    }

    {AnyFPath}    {
        chkLOC();
        onPathlikeMatched(yytext(), '/', false, yychar);
    }
}

<YYINITIAL, DATATYPE, SUBSHELL, STRING, COMMENT, SCOMMENT, QSTRING, HERESTRING,
    HEREQSTRING> {

    {WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
    [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
    [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<STRING, SCOMMENT, QSTRING> {
{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          chkLOC();
          onEmailAddressMatched(yytext(), yychar);
        }
}

<STRING, SCOMMENT> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        chkLOC();
        onUriMatched(yytext(), yychar, PoshUtils.STRINGLITERAL_APOS_DELIMITER);
    }
}

<COMMENT> {
    {BrowseableURI} \>?    {
        onUriMatched(yytext(), yychar, PoshUtils.MAYBE_END_MULTILINE_COMMENT);
    }
}
