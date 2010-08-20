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
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis.sh;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;
import java.util.Stack;

%%
%public
%class ShXref
%extends JFlexXref
%unicode
%ignorecase
%int
%{
  private final Stack<Integer> stateStack = new Stack<Integer>();
  private final Stack<String> styleStack = new Stack<String>();

  @Override
  public void reInit(char[] contents, int length) {
    super.reInit(contents, length);
    stateStack.clear();
    styleStack.clear();
  }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }

  private void pushstate(int state, String style) throws IOException {
    if (!styleStack.empty()) {
      out.write("</span>");
    }
    if (style == null) {
      out.write("<span>");
    } else {
      out.write("<span class=\"" + style + "\">");
    }
    stateStack.push(yystate());
    styleStack.push(style);
    yybegin(state);
  }

  private void popstate() throws IOException {
    out.write("</span>");
    yybegin(stateStack.pop());
    styleStack.pop();
    if (!styleStack.empty()) {
      String style = styleStack.peek();
      if (style == null) {
        out.write("<span>");
      } else {
        out.write("<span class=\"" + style + "\">");
      }
    }
  }

%}

WhiteSpace     = [ \t\f]
EOL = \r|\n|\r\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]+
Number = \$? [0-9]+\.[0-9]+|[0-9][0-9]*|"0x" [0-9a-fA-F]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([a-zA-Z]+)
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*)+[a-zA-Z0-9]

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
 */
%state STRING SCOMMENT QSTRING SUBSHELL BACKQUOTE BRACEGROUP

%%
<STRING>{
 "$" {Identifier} {
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
  \$\{ {Identifier} \[\" {
    out.write(yytext()); pushstate(STRING, "s");
  }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP> {
\$ ? {Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.shkwd, yyline);
}

{Number}        { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

 \$ ? \" { pushstate(STRING, "s"); out.write(yytext()); }
 \$ ? \' { pushstate(QSTRING, "s"); out.write(yytext()); }
 "#"     { pushstate(SCOMMENT, "c"); out.write(yytext()); }
}

<STRING> {
 \" {WhiteSpace}* \"  { out.write(yytext()); }
 \"     { out.write(yytext()); popstate(); }
 \\\\ | \\\" | \\\$ | \\` { out.write(yytext()); }
 \$\(   { pushstate(SUBSHELL, null); out.write(yytext()); }
 `      { pushstate(BACKQUOTE, null); out.write(yytext()); }

 /* Bug #15661: Recognize ksh command substitution within strings. According
  * to ksh man page http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the opening brace must be followed by a blank.
  */
 "${" / {WhiteSpace} | {EOL} {
   pushstate(BRACEGROUP, null); out.write(yytext());
 }
}

<QSTRING> {
 \' {WhiteSpace}* \' { out.write(yytext()); }
 \\'  { out.write("\\'"); }
 \'   { out.write(yytext()); popstate(); }
}

<SCOMMENT> {
{EOL} { popstate();
     startNewLine();}
}

<SUBSHELL> {
  \)   { out.write(yytext()); popstate(); }
}

<BACKQUOTE> {
  ` { out.write(yytext()); popstate(); }
}

<BRACEGROUP> {
 /* Bug #15661: Terminate a ksh brace group. According to ksh man page
  * http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the closing brace must be on beginning of line, or it must be preceeded by
  * a semi-colon and (optionally) whitespace.
  */
  ^ {WhiteSpace}* \}  { out.write(yytext()); popstate(); }
  ; {WhiteSpace}* \}  { out.write(yytext()); popstate(); }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP> {
  /* Don't enter new state if special character is escaped. */
  \\` | \\\( | \\\) | \\\\ | \\\{ { out.write(yytext()); }
  \\\" | \\' | \\\$ | \\\# { out.write(yytext()); }

  /* $# should not start a comment. */
  "$#" { out.write(yytext()); }

  \$ ? \( { pushstate(SUBSHELL, null); out.write(yytext()); }
  ` { pushstate(BACKQUOTE, null); out.write(yytext()); }

 /* Bug #15661: Recognize ksh command substitution within strings. According
  * to ksh man page http://www2.research.att.com/~gsf/man/man1/ksh-man.html#Command%20Substitution
  * the opening brace must be followed by a blank. Make the initial dollar sign
  * optional so that we get the nesting right and don't terminate the brace
  * group too early if the ${ cmd; } expression contains nested { cmd; } groups.
  */
  \$ ? \{ / {WhiteSpace} | {EOL} {
    pushstate(BRACEGROUP, null); out.write(yytext());
  }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, BRACEGROUP, STRING, SCOMMENT, QSTRING> {
{File} {
    String path = yytext();
    out.write("<a href=\""+urlPrefix+"path=");
    out.write(path);
    appendProject();
    out.write("\">");
    out.write(path);
    out.write("</a>");
}

{Path}
        { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}
"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
 {EOL}  { startNewLine(); }
{WhiteSpace}+   { out.write(yytext()); }
[!-~]   { out.write(yycharat(0)); }
 .      { writeUnicodeChar(yycharat(0)); }
}

<STRING, SCOMMENT, QSTRING> {

("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
{
    String url = yytext();
    out.write("<a href=\"");
    out.write(url);out.write("\">");
    out.write(url);out.write("</a>");
}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          out.write(yytext().replace("@", " (at] "));
        }
}

<<EOF>> {
    // If we reach EOF while being in a nested state, pop all the way up
    // the initial state so that we close open HTML tags.
    while (!stateStack.isEmpty()) {
        popstate();
    }
    return YYEOF;
}
