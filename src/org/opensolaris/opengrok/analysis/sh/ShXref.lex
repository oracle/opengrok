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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.sh;

import java.io.IOException;
import java.util.Stack;
import org.opensolaris.opengrok.analysis.JFlexSymbolMatcher;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
%%
%public
%class ShXref
%extends JFlexSymbolMatcher
%unicode
%int
%char
%init{
    yyline = 1;
%init}
%include CommonLexer.lexh
%{
  private final Stack<String> styleStack = new Stack<String>();

  // State variables for the HEREDOC state. They tell what the stop word is,
  // and whether leading tabs should be removed from the input lines before
  // comparing with the stop word.
  private String heredocStopWord;
  private boolean heredocStripLeadingTabs;

  /**
   * Resets the sh tracked state; {@inheritDoc}
   */
  @Override
  public void reset() {
      super.reset();
      heredocStopWord = null;
      heredocStripLeadingTabs = false;
  }

  @Override
  protected void clearStack() {
      super.clearStack();
      styleStack.clear();
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

  /**
   * Check the contents of a line to see if it matches the stop word for a
   * here-document.
   *
   * @param line a line in the input file
   * @return true if the line terminates a here-document, false otherwise
   */
  private boolean isHeredocStopWord(String line) {
    // Skip leading tabs if heredocStripLeadingTabs is true.
    int i = 0;
    while (heredocStripLeadingTabs &&
              i < line.length() && line.charAt(i) == '\t') {
      i++;
    }

    // Compare remaining characters on the line with the stop word.
    return line.substring(i).equals(heredocStopWord);
  }

%}

File = {FNameChar}+ "." ([a-zA-Z]+)

/*
 * States:
 * STRING - double-quoted string, ex: "hello, world!"
 * SCOMMENT - single-line comment, ex: # this is a comment
 * QSTRING - single-quoted string, ex: 'hello, world!'
 * SUBSHELL - commands executed in a sub-shell,
 *               example 1: (echo $header; cat file.txt)
 *               example 2 (command substitution): $(cat file.txt)
 * BACKQUOTE - command substitution using back-quotes, ex: `cat file.txt`
 * BRACEGROUP - group of commands in braces, possibly ksh command substitution
 *              extension, ex: ${ cat file.txt; }
 * HEREDOC - here-document, example: cat<<EOF ... EOF
 */
%state STRING SCOMMENT QSTRING SUBSHELL BACKQUOTE BRACEGROUP HEREDOC

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include CommonLaxFPath.lexh
%include Sh.lexh
%%
<STRING>{
 "$" {Identifier}    {
    String id = yytext();
    onRefsTermMatched(id, yychar);
 }

  /* This rule matches associative arrays inside strings,
     for instance "${array["string"]}". Push a new STRING
     state on the stack to prevent premature exit from the
     STRING state. */
  \$\{ {Identifier} \[\"    {
    onNonSymbolMatched(yytext(), yychar);
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
  }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP> {
\$ ? {Identifier}    {
    String id = yytext();
    onFilteredSymbolMatched(id, yychar, Consts.shkwd);
 }

{Number}        {
    String lastClassName = getDisjointSpanClassName();
    onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(lastClassName, yychar);
 }

 \$ ? \"    {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
 \$ ? \'    {
    pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }
 "#"     {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    onNonSymbolMatched(yytext(), yychar);
 }

 // Recognize here-documents. At least a subset of them.
 "<<" "-"? {WhspChar}* {Identifier} {WhspChar}*    {
   String text = yytext();
   onNonSymbolMatched(text, yychar);

   heredocStripLeadingTabs = (text.charAt(2) == '-');
   heredocStopWord = text.substring(heredocStripLeadingTabs ? 3 : 2).trim();
   pushSpan(HEREDOC, HtmlConsts.STRING_CLASS);
 }

 // Any sequence of more than two < characters should not start HEREDOC. Use
 // this rule to catch them before the HEREDOC rule.
 "<<" "<" +    {
   onNonSymbolMatched(yytext(), yychar);
 }

 {Unary_op_req_lookahead} / \W    {
    onNonSymbolMatched(yytext(), yychar);
 }
 {Unary_op_req_lookahead} $    {
    onNonSymbolMatched(yytext(), yychar);
 }
 {WhspChar}+ {Unary_op_char} / ")"    {
    onNonSymbolMatched(yytext(), yychar);
 }
 {Binary_op}    {
    onNonSymbolMatched(yytext(), yychar);
 }
}

<STRING> {
 \\[\"\$\`\\] |
 \" {WhspChar}* \"    { onNonSymbolMatched(yytext(), yychar); }
 \"     { onNonSymbolMatched(yytext(), yychar); yypop(); }
 \$\(    {
    pushSpan(SUBSHELL, null);
    onNonSymbolMatched(yytext(), yychar);
 }
 [`]    {
    pushSpan(BACKQUOTE, null);
    onNonSymbolMatched(yytext(), yychar);
 }

 /* Bug #15661: Recognize ksh command substitution within strings. According
  * to ksh man page http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the opening brace must be followed by a blank.
  */
 "${" / {WhspChar} | {EOL}    {
    pushSpan(BRACEGROUP, null);
    onNonSymbolMatched(yytext(), yychar);
 }
}

<QSTRING> {
 \\[\'] |
 \' {WhspChar}* \'    { onNonSymbolMatched(yytext(), yychar); }
 \'    { onNonSymbolMatched(yytext(), yychar); yypop(); }
}

<SCOMMENT> {
{WhspChar}*{EOL}    {
    yypop();
    onEndOfLineMatched(yytext(), yychar);
 }
}

<SUBSHELL> {
  \)   { onNonSymbolMatched(yytext(), yychar); yypop(); }
}

<BACKQUOTE> {
  [`]    { onNonSymbolMatched(yytext(), yychar); yypop(); }
}

<BRACEGROUP> {
 /* Bug #15661: Terminate a ksh brace group. According to ksh man page
  * http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the closing brace must be on beginning of line, or it must be preceded by
  * a semi-colon and (optionally) whitespace.
  */
  ^ {WhspChar}* \}    { onNonSymbolMatched(yytext(), yychar); yypop(); }
  ; {WhspChar}* \}    { onNonSymbolMatched(yytext(), yychar); yypop(); }
}

<HEREDOC> {
  [^\n]+    {
    String line = yytext();
    if (isHeredocStopWord(line)) {
      yypop();
    }
    onNonSymbolMatched(line, yychar);
  }

  {EOL}    { onEndOfLineMatched(yytext(), yychar); }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP> {
  /* Don't enter new state if special character is escaped. */
  \\[`\)\(\{\"\'\$\#\\]    { onNonSymbolMatched(yytext(), yychar); }

  /* $# should not start a comment. */
  "$#"    { onNonSymbolMatched(yytext(), yychar); }

  \$ ? \(    {
    pushSpan(SUBSHELL, null);
    onNonSymbolMatched(yytext(), yychar);
  }
  [`]    {
    pushSpan(BACKQUOTE, null);
    onNonSymbolMatched(yytext(), yychar);
  }

 /* Bug #15661: Recognize ksh command substitution within strings. According
  * to ksh man page http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the opening brace must be followed by a blank. Make the initial dollar sign
  * optional so that we get the nesting right and don't terminate the brace
  * group too early if the ${ cmd; } expression contains nested { cmd; } groups.
  */
  \$ ? \{ / {WhspChar} | {EOL}    {
    pushSpan(BRACEGROUP, null);
    onNonSymbolMatched(yytext(), yychar);
  }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP, STRING, SCOMMENT, QSTRING> {
{File}    {
    String path = yytext();
    onFilelikeMatched(path, yychar);
}

{RelaxedMiddleFPath}    {
    onPathlikeMatched(yytext(), '/', false, yychar); }

{WhspChar}*{EOL}    { onEndOfLineMatched(yytext(), yychar); }
[^\n]    { onNonSymbolMatched(yytext(), yychar); }
}

<STRING, SCOMMENT, QSTRING> {
{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+    {
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
        onUriMatched(yytext(), yychar, StringUtils.APOS_NO_BSESC);
    }
}
