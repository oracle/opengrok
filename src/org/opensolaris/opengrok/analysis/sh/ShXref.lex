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
import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class ShXref
%extends JFlexXrefSimple
%unicode
%int
%include CommonXref.lexh
%{
  private final Stack<String> styleStack = new Stack<String>();

  // State variables for the HEREDOC state. They tell what the stop word is,
  // and whether leading tabs should be removed from the input lines before
  // comparing with the stop word.
  private String heredocStopWord;
  private boolean heredocStripLeadingTabs;

  @Override
  protected void clearStack() {
      super.clearStack();
      styleStack.clear();
  }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }

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
    out.write("<a href=\"");
    out.write(urlPrefix);
    out.write("refs=");
    out.write(id);
    appendProject();
    out.write("\">");
    out.write(id);
    out.write("</a>");
 }

  /* This rule matches associative arrays inside strings,
     for instance "${array["string"]}". Push a new STRING
     state on the stack to prevent premature exit from the
     STRING state. */
  \$\{ {Identifier} \[\"    {
    out.write(htmlize(yytext()));
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
  }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP> {
\$ ? {Identifier}    {
    String id = yytext();
    writeSymbol(id, Consts.shkwd, yyline);
 }

{Number}        {
    String lastClassName = getDisjointSpanClassName();
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(lastClassName);
 }

 \$ ? \"    {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \$ ? \'    {
    pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 "#"     {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
 }

 // Recognize here-documents. At least a subset of them.
 "<<" "-"? {WhspChar}* {Identifier} {WhspChar}*    {
   String text = yytext();
   out.write(htmlize(text));

   heredocStripLeadingTabs = (text.charAt(2) == '-');
   heredocStopWord = text.substring(heredocStripLeadingTabs ? 3 : 2).trim();
   pushSpan(HEREDOC, HtmlConsts.STRING_CLASS);
 }

 // Any sequence of more than two < characters should not start HEREDOC. Use
 // this rule to catch them before the HEREDOC rule.
 "<<" "<" +    {
   out.write(htmlize(yytext()));
 }

 {Unary_op_req_lookahead} / \W    {
    out.write(yytext());
 }
 {Unary_op_req_lookahead} $    {
    out.write(yytext());
 }
 {WhiteSpace} {Unary_op_char} / ")"    {
    out.write(yytext());
 }
 {Binary_op}    {
    out.write(yytext());
 }
}

<STRING> {
 \\[\"\$\`\\] |
 \" {WhspChar}* \"    { out.write(htmlize(yytext())); }
 \"     { out.write(htmlize(yytext())); yypop(); }
 \$\(    {
    pushSpan(SUBSHELL, null);
    out.write(yytext());
 }
 [`]    {
    pushSpan(BACKQUOTE, null);
    out.write(yytext());
 }

 /* Bug #15661: Recognize ksh command substitution within strings. According
  * to ksh man page http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the opening brace must be followed by a blank.
  */
 "${" / {WhspChar} | {EOL}    {
    pushSpan(BRACEGROUP, null);
    out.write(yytext());
 }
}

<QSTRING> {
 \\[\'] |
 \' {WhspChar}* \'    { out.write(htmlize(yytext())); }
 \'    { out.write(htmlize(yytext())); yypop(); }
}

<SCOMMENT> {
{WhspChar}*{EOL}    {
    yypop();
    startNewLine();
 }
}

<SUBSHELL> {
  \)   { out.write(yytext()); yypop(); }
}

<BACKQUOTE> {
  [`]    { out.write(yytext()); yypop(); }
}

<BRACEGROUP> {
 /* Bug #15661: Terminate a ksh brace group. According to ksh man page
  * http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the closing brace must be on beginning of line, or it must be preceded by
  * a semi-colon and (optionally) whitespace.
  */
  ^ {WhspChar}* \}    { out.write(yytext()); yypop(); }
  ; {WhspChar}* \}    { out.write(yytext()); yypop(); }
}

<HEREDOC> {
  [^\n]+    {
    String line = yytext();
    if (isHeredocStopWord(line)) {
      yypop();
    }
    out.write(htmlize(line));
  }

  {EOL}    { startNewLine(); }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP> {
  /* Don't enter new state if special character is escaped. */
  \\[`\)\(\{\"\'\$\#\\]    { out.write(htmlize(yytext())); }

  /* $# should not start a comment. */
  "$#"    { out.write(yytext()); }

  \$ ? \(    { pushSpan(SUBSHELL, null); out.write(yytext()); }
  [`]    { pushSpan(BACKQUOTE, null); out.write(yytext()); }

 /* Bug #15661: Recognize ksh command substitution within strings. According
  * to ksh man page http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the opening brace must be followed by a blank. Make the initial dollar sign
  * optional so that we get the nesting right and don't terminate the brace
  * group too early if the ${ cmd; } expression contains nested { cmd; } groups.
  */
  \$ ? \{ / {WhspChar} | {EOL}    {
    pushSpan(BRACEGROUP, null); out.write(yytext());
  }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP, STRING, SCOMMENT, QSTRING> {
{File}    {
    String path = yytext();
    out.write("<a href=\""+urlPrefix+"path=");
    out.write(path);
    appendProject();
    out.write("\">");
    out.write(path);
    out.write("</a>");
}

{RelaxedMiddleFPath}    {
    out.write(Util.breadcrumbPath(urlPrefix + "path=", yytext(), '/')); }

[&<>\'\"]    { out.write(htmlize(yytext())); }
{WhspChar}*{EOL}    { startNewLine(); }
{WhiteSpace}    { out.write(yytext()); }
[!-~]    { out.write(yycharat(0)); }
[^\n]    { writeUnicodeChar(yycharat(0)); }
}

<STRING, SCOMMENT, QSTRING> {
{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+    {
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
        appendLink(yytext(), true, StringUtils.APOS_NO_BSESC);
    }
}
