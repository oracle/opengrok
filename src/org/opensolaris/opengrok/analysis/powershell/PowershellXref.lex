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
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.analysis.ScopeAction;
import org.opensolaris.opengrok.web.HtmlConsts;
import java.util.Stack;
import java.util.regex.Matcher;
%%
%public
%class PoshXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%init{
    yyline = 1;
%init}
%include CommonLexer.lexh
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
 \{     { onScopeChanged(ScopeAction.INC, yytext(), yychar); }
 \}     { onScopeChanged(ScopeAction.DEC, yytext(), yychar); }
 \;     { onScopeChanged(ScopeAction.END, yytext(), yychar); }
}

<YYINITIAL, SUBSHELL> {
 ^ {Label}    {
    String capture = yytext();
    onLabelMatched(capture, yychar, capture.substring(1));
 }
 {Break} |
 {Continue}    {
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
        onKeywordMatched(capture, yychar);
    } else {
        String sigil = capture.substring(0, 1);
        String id = capture.substring(1);
        onNonSymbolMatched(sigil, yychar);
        onFilteredSymbolMatched(id, yychar, Consts.poshkwd, false);
    }
 }

 {Number}    {
    String lastClassName = getDisjointSpanClassName();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(lastClassName, yychar);
 }

 \"    {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
 \'    {
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
    pushSpan(HERESTRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
 \@\'    {
    pushSpan(HEREQSTRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<DOTSYNTAX> {
 "."    {
    onNonSymbolMatched(yytext(), yychar);
 }

 [^]    {
    yypushback(yylength());
    yypop();
 }
}

<YYINITIAL, SUBSHELL, DATATYPE, DOTSYNTAX> {
 {Identifier}    {
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.poshkwd, false);
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
 \"\"    { onNonSymbolMatched(yytext(), yychar); }

 \$? \"    {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
 }
}

<STRING, HERESTRING> {
 "$("    { pushSpan(SUBSHELL, null); onNonSymbolMatched(yytext(), yychar); }
}

<QSTRING> {
 \'\'    { onNonSymbolMatched(yytext(), yychar); }
 \'    {
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
    onNonSymbolMatched(yytext(), yychar);
    yypop();
  }
}

<HERESTRING> {
  // Match escaped dollar sign of variable 
  // (eg. `$var) so it does not turn into web-link.
  "`$"    { onNonSymbolMatched(yytext(), yychar); }

  {SimpleVariable}    {
     emitSimpleVariable();
  }

  {ComplexVariable}    {
     emitComplexVariable();
  }
  ^ \"\@    {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
  }
}

<HEREQSTRING> {
  ^ "'@"    {
    onNonSymbolMatched(yytext(), yychar);
    yypop();
  }
}

<YYINITIAL, SUBSHELL> {
  /* Don't enter new state if special character is escaped. */
  [`][`\(\)\{\}\"\'\$\#\\]    { onNonSymbolMatched(yytext(), yychar); }

  /* $# should not start a comment. */
  "$#"    { onNonSymbolMatched(yytext(), yychar); }

  \$ ? \(    { pushSpan(SUBSHELL, null); onNonSymbolMatched(yytext(), yychar); }
}

<YYINITIAL, SUBSHELL, STRING, SCOMMENT, QSTRING> {
    {File} {
        String path = yytext();
        onFilelikeMatched(path, yychar);
    }

    {AnyFPath}
            {onPathlikeMatched(yytext(), '/', false, yychar);}
}

<YYINITIAL, DATATYPE, SUBSHELL, STRING, COMMENT, SCOMMENT, QSTRING, HERESTRING,
    HEREQSTRING> {

    {WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
    [^\n]    { onNonSymbolMatched(yytext(), yychar); }
}

<STRING, SCOMMENT, QSTRING> {
{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          onEmailAddressMatched(yytext(), yychar);
        }
}

<STRING, SCOMMENT> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        onUriMatched(yytext(), yychar, PoshUtils.STRINGLITERAL_APOS_DELIMITER);
    }
}

<COMMENT> {
    {BrowseableURI} \>?    {
        onUriMatched(yytext(), yychar, PoshUtils.MAYBE_END_MULTILINE_COMMENT);
    }
}
