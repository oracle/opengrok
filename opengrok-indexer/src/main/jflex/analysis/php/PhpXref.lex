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
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a PHP file
 */

package org.opengrok.indexer.analysis.php;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.EmphasisHint;
import org.opengrok.indexer.web.HtmlConsts;
%%
%public
%class PhpXref
%extends JFlexSymbolMatcher
%unicode
%ignorecase
%int
%char
%include ../CommonLexer.lexh
%include ../CommonXref.lexh
%{
  private final static Set<String> PSEUDO_TYPES;
  private final Stack<String> popStrings = new Stack<>();
  private final Stack<String> docLabels = new Stack<String>();

  static {
    PSEUDO_TYPES = new HashSet<String>(Arrays.asList(
        new String[] {
            "string", "integer", "int", "boolean", "bool", "float", "double",
            "object", "mixed", "array", "resource", "void", "null", "callback",
            "false", "true", "self", "callable"
        }
    ));
  }

  @Override
  protected void clearStack() {
      super.clearStack();
      popStrings.clear();
      docLabels.clear();
  }

  /**
   * save current yy state to stack
   * @param newState state id
   * @param popString string for the state
   */
  public void yypush(int newState, String popString) {
      super.yypush(newState);
      popStrings.push(popString);
  }

  /**
   * save current yy state to stack
   * @param newState state id
   */
  @Override
  public void yypush(int newState) {
      yypush(newState, null);
  }

  /**
   * pop last state from stack
   * @throws IOException in case of any I/O problem
   */
  @Override
  public void yypop() throws IOException {
      String popString = popStrings.pop();
      if (popString != null) {
          onDisjointSpanChanged(popString, yychar);
      }
      super.yypop();
  }

  private void writeDocTag() throws IOException {
    String capture = yytext();
    String sigil = capture.substring(0, 1);
    String tag = capture.substring(1);
    onNonSymbolMatched(sigil, yychar);
    onNonSymbolMatched(tag, EmphasisHint.STRONG, yychar);
  }

  private boolean isTabOrSpace(int i) {
    return yycharat(i) == '\t' || yycharat(i) == ' ';
  }

  private static boolean isHtmlState(int state) {
    return state == TAG_NAME            || state == AFTER_TAG_NAME
        || state == ATTRIBUTE_NOQUOTE   || state == ATTRIBUTE_SINGLE
        || state == ATTRIBUTE_DOUBLE    || state == HTMLCOMMENT
        || state == YYINITIAL;
  }

  protected void chkLOC() {
      switch (yystate()) {
          case HTMLCOMMENT:
          case SCOMMENT:
          case COMMENT:
          case DOCCOMMENT:
          case DOCCOM_TYPE_THEN_NAME:
          case DOCCOM_NAME:
          case DOCCOM_TYPE:
              break;
          default:
              phLOC();
              break;
      }
  }
%}

Identifier = [a-zA-Z_\u007F-\u10FFFF] [a-zA-Z0-9_\u007F-\u10FFFF]*

File = [a-zA-Z]{FNameChar}* "." ("php"|"php3"|"php4"|"phps"|"phtml"|"inc"|"diff"|"patch")

BinaryNumber = 0[b|B][01]+
OctalNumber = 0[0-7]+
DecimalNumber = [1-9][0-9]+
HexadecimalNumber = 0[xX][0-9a-fA-F]+
FloatNumber = (([0-9]* "." [0-9]+) | ([0-9]+ "." [0-9]*) | [0-9]+)([eE][+-]?[0-9]+)?
Number = [+-]?({BinaryNumber}|{OctalNumber}|{DecimalNumber}|{HexadecimalNumber}|{FloatNumber})

//do not support <script language="php"> and </script> opening/closing tags
OpeningTag = ("<?" "php"?) | "<?="
ClosingTag = "?>"

CastTypes = "int"|"integer"|"real"|"double"|"float"|"string"|"binary"|"array"
            |"object"|"bool"|"boolean"|"unset"

DoubleQuoteEscapeSequences = \\ (([nrtfve\\$]) | ([xX] [0-9a-fA-F]{1,2}) |  ([0-7]{1,3}))
SingleQuoteEscapeSequences = \\ [\\\']

DocPreviousChar = "*" | {WhspChar}+

//does not supported nested type expressions like ((array|integer)[]|boolean)[]
//that would require additional states
DocType = {IndividualDocType} (\| {IndividualDocType})*
IndividualDocType = ({SimpleDocType} "[]"? | ( \( {SimpleDocType} "[]"? ( \| {SimpleDocType} "[]"? )* \)\[\] ))
SimpleDocType = {Identifier}

DocParamWithType = "return" | "throws" | "throw" | "var" | "see"  //"see" can take a URL
DocParamWithTypeAndName = "param" | "global" | "property" | "property-read"
                          | "property-write"
DocParamWithName = "uses"
DocInlineTags = "internal" | "inheritDoc" | "link" | "example"
//method needs special treatment

HtmlNameStart = [a-zA-Z_\u00C0-\u10FFFFFF]
HtmlName      = {HtmlNameStart} ({HtmlNameStart} | [\-.0-9\u00B7])*

%state TAG_NAME AFTER_TAG_NAME ATTRIBUTE_NOQUOTE ATTRIBUTE_SINGLE ATTRIBUTE_DOUBLE HTMLCOMMENT
%state IN_SCRIPT STRING SCOMMENT HEREDOC NOWDOC COMMENT QSTRING BACKQUOTE STRINGEXPR STRINGVAR
%state DOCCOMMENT DOCCOM_TYPE_THEN_NAME DOCCOM_NAME DOCCOM_TYPE

%include ../Common.lexh
%include ../CommonURI.lexh
%include ../CommonPath.lexh
%%
<YYINITIAL> { //HTML
    "<" | "</"      {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypush(TAG_NAME);
    }

    "<!--" {
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        yybegin(HTMLCOMMENT);
    }
}

<TAG_NAME> {
    {HtmlName} {
        chkLOC();
        String lastClassName = getDisjointSpanClassName();
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(lastClassName, yychar);
        yybegin(AFTER_TAG_NAME);
    }

    {HtmlName}:{HtmlName} {
        chkLOC();
        String lastClassName = getDisjointSpanClassName();
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        int i = 0;
        while (yycharat(i) != ':') i++;
        onNonSymbolMatched(yytext().substring(0,i), yychar);
        onDisjointSpanChanged(null, yychar);
        onNonSymbolMatched(":", yychar);
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext().substring(i + 1), yychar);
        onDisjointSpanChanged(lastClassName, yychar);
        yybegin(AFTER_TAG_NAME);
    }
}

<AFTER_TAG_NAME> {
    {HtmlName} {
        chkLOC();
        //attribute
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
    }

    "=" {WhspChar}* (\" | \')? {
        chkLOC();
        char attributeDelim = yycharat(yylength()-1);
        onNonSymbolMatched("=", yychar);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext().substring(1), yychar);
        if (attributeDelim == '\'') {
            yypush(ATTRIBUTE_SINGLE);
        } else if (attributeDelim == '"') {
            yypush(ATTRIBUTE_DOUBLE);
        } else {
            yypush(ATTRIBUTE_NOQUOTE);
        }
    }
}

<TAG_NAME, AFTER_TAG_NAME> {
    ">"     {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypop(); //to YYINITIAL
    }
}

<YYINITIAL, TAG_NAME, AFTER_TAG_NAME> {
    {OpeningTag}    {
        chkLOC();
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
        yypush(IN_SCRIPT); }
}

<ATTRIBUTE_NOQUOTE> {
    {WhspChar}* {EOL} {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        yypop();
    }
    {WhspChar}+   {
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
        yypop();
    }
    ">"     {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
        //pop twice
        yypop();
        yypop();
    }
}

<ATTRIBUTE_DOUBLE>\" {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar); yypop();
}
<ATTRIBUTE_SINGLE>\' {
    chkLOC();
    onNonSymbolMatched(yytext(), yychar);
    onDisjointSpanChanged(null, yychar); yypop();
}

<ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE> {
    {WhspChar}* {EOL} {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    }
}

<ATTRIBUTE_NOQUOTE, ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE> {
    {OpeningTag} {
        chkLOC();
        onDisjointSpanChanged(null, yychar);
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
        yypush(IN_SCRIPT, HtmlConsts.STRING_CLASS);
    }
}

<HTMLCOMMENT> {
    "-->" {
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
        yybegin(YYINITIAL);
    }

    {WhspChar}* {EOL} {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    }

    {OpeningTag} {
        onDisjointSpanChanged(null, yychar);
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
        yypush(IN_SCRIPT, HtmlConsts.COMMENT_CLASS);
    }
}

<IN_SCRIPT> {
    "$" {Identifier} {
        chkLOC();
        //we ignore keywords if the identifier starts with one of variable chars
        String id = yytext().substring(1);
        onNonSymbolMatched("$", yychar);
        onFilteredSymbolMatched(id, yychar, null);
    }

    {Identifier} {
        chkLOC();
        onFilteredSymbolMatched(yytext(), yychar, Consts.kwd);
    }

    \( {WhspChar}* {CastTypes} {WhspChar}* \) {
        chkLOC();
        onNonSymbolMatched("(", yychar);
        int i = 1, j;
        while (isTabOrSpace(i)) { onNonSymbolMatched(yycharat(i++), yychar); }

        j = i + 1;
        while (!isTabOrSpace(j) && yycharat(j) != ')') { j++; }
        onNonSymbolMatched(yytext().substring(i, j), EmphasisHint.EM, yychar);

        onNonSymbolMatched(yytext().substring(j, yylength()), yychar);
    }

    b? \" {
        chkLOC();
        yypush(STRING);
        if (yycharat(0) == 'b') { onNonSymbolMatched('b', yychar); }
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched("\"", yychar);
    }

    b? \' {
        chkLOC();
        yypush(QSTRING);
        if (yycharat(0) == 'b') { onNonSymbolMatched('b', yychar); }
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched("\'", yychar);
    }

    [`]    {
        chkLOC();
        yypush(BACKQUOTE);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }

    b? "<<<" {WhspChar}* ({Identifier} | (\'{Identifier}\') | (\"{Identifier}\")){EOL} {
        chkLOC();
        if (yycharat(0) == 'b') { onNonSymbolMatched('b', yychar); }
        onNonSymbolMatched("<<<", yychar);
        int i = yycharat(0) == 'b' ? 4 : 3, j = yylength()-1;
        while (isTabOrSpace(i)) {
            onNonSymbolMatched(yycharat(i++), yychar);
        }
        while (yycharat(j) == '\n' || yycharat(j) == '\r') { j--; }

        if (yycharat(i) == '\'' || yycharat(i) == '"') {
            yypush(NOWDOC);
            String text = yytext().substring(i+1, j);
            this.docLabels.push(text);
            onNonSymbolMatched(String.valueOf(yycharat(i)), yychar);
            onDisjointSpanChanged(HtmlConsts.BOLD_CLASS, yychar);
            onNonSymbolMatched(text, yychar);
            onDisjointSpanChanged(null, yychar);
            onNonSymbolMatched(String.valueOf(yycharat(i)), yychar);
        } else {
            yypush(HEREDOC);
            String text = yytext().substring(i, j+1);
            this.docLabels.push(text);
            onDisjointSpanChanged(HtmlConsts.BOLD_CLASS, yychar);
            onNonSymbolMatched(text, yychar);
            onDisjointSpanChanged(null, yychar);
        }
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
    }

    {Number}   {
        chkLOC();
        String lastClassName = getDisjointSpanClassName();
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(lastClassName, yychar);
    }

    "#"|"//"   {
        yypush(SCOMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }
    "/**"      {
        yypush(DOCCOMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched("/*", yychar);
        yypushback(1);
    }
    "/*"       {
        yypush(COMMENT);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
        onNonSymbolMatched(yytext(), yychar);
    }

    \{         {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        yypush(IN_SCRIPT);
    }
    \}         {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        if (!this.stack.empty() && !isHtmlState(this.stack.peek()))
            yypop(); //may pop STRINGEXPR/HEREDOC/BACKQUOTE
        /* we don't pop unconditionally because we can exit a ?php block with
         * with open braces and we discard the information about the number of
         * open braces when exiting the block (see the action for {ClosingTag}
         * below. An alternative would be keeping two stacks -- one for HTML
         * and another for PHP. The PHP scanner only needs one stack because
         * it doesn't need to keep state about the HTML */
    }

    {ClosingTag} {
        chkLOC();
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
        while (!isHtmlState(yystate()))
            yypop();
    }
} //end of IN_SCRIPT

<STRING> {
    \\\"    {
        chkLOC();
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
    }
    \"    {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar); yypop();
    }
}

<BACKQUOTE> {
    "\\`"    {
        chkLOC();
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
    }
    "`"    {
        chkLOC();
        onNonSymbolMatched("`", yychar);
        onDisjointSpanChanged(null, yychar); yypop();
    }
}

<STRING, BACKQUOTE, HEREDOC> {
    "\\{" {
        chkLOC();
        onNonSymbolMatched(yytext(), yychar);
    }

    {DoubleQuoteEscapeSequences} {
        chkLOC();
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
    }

    "$"     {
        chkLOC();
        onDisjointSpanChanged(null, yychar);
        onNonSymbolMatched("$", yychar);
        yypush(STRINGVAR, HtmlConsts.STRING_CLASS);
    }

    "${" {
        chkLOC();
        onDisjointSpanChanged(null, yychar);
        onNonSymbolMatched(yytext(), yychar);
        yypush(STRINGEXPR, HtmlConsts.STRING_CLASS);
    }

    /* ${ is different from {$ -- for instance {$foo->bar[1]} is valid
     * but ${foo->bar[1]} is not. ${ only enters the full blown scripting state
     * when {Identifer}[ is found (see the PHP scanner). Tthe parser seems to
     * put more restrictions on the {$ scripting mode than on the
     * "${" {Identifer} "[" scripting mode, but that's not relevant here */
    "{$" {
        chkLOC();
        onDisjointSpanChanged(null, yychar);
        onNonSymbolMatched("{", yychar);
        yypushback(1);
        yypush(IN_SCRIPT, HtmlConsts.STRING_CLASS);
    }
}

<QSTRING> {
    {SingleQuoteEscapeSequences} {
        chkLOC();
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
    }

    \'      {
        chkLOC();
        onNonSymbolMatched("'", yychar);
        onDisjointSpanChanged(null, yychar); yypop();
    }
}

<HEREDOC, NOWDOC>^{Identifier} ";"? {EOL}  {
    chkLOC();
    int i = yylength() - 1;
    boolean hasSemi = false;
    while (yycharat(i) == '\n' || yycharat(i) == '\r') { i--; }
    if (yycharat(i) == ';') { hasSemi = true; i--; }
    if (yytext().substring(0, i+1).equals(this.docLabels.peek())) {
        String text = this.docLabels.pop();
        yypop();
        onDisjointSpanChanged(HtmlConsts.BOLD_CLASS, yychar);
        onNonSymbolMatched(text, yychar);
        onDisjointSpanChanged(null, yychar);
        if (hasSemi) onNonSymbolMatched(";", yychar);
        onEndOfLineMatched(yytext(), yychar);
    } else {
        onNonSymbolMatched(yytext().substring(0,i+1), yychar);
        if (hasSemi) onNonSymbolMatched(";", yychar);
        onEndOfLineMatched(yytext(), yychar);
    }
}

<STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC>{WhspChar}* {EOL} {
    onDisjointSpanChanged(null, yychar);
    onEndOfLineMatched(yytext(), yychar);
    onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
}

<STRINGVAR> {
    {Identifier}    {
        chkLOC();
        onFilteredSymbolMatched(yytext(), yychar, null);
    }

    \[ {Number} \] {
        chkLOC();
        onNonSymbolMatched("[", yychar);
        String lastClassName = getDisjointSpanClassName();
        onDisjointSpanChanged(HtmlConsts.NUMBER_CLASS, yychar);
        onNonSymbolMatched(yytext().substring(1, yylength()-1), yychar);
        onDisjointSpanChanged(lastClassName, yychar);
        onNonSymbolMatched("]", yychar);
        yypop(); //because "$arr[0][1]" is the same as $arr[0] . "[1]"
    }

    \[ {Identifier} \] {
        chkLOC();
        //then the identifier is actually a string!
        onNonSymbolMatched("[", yychar);
        String lastClassName = getDisjointSpanClassName();
        onDisjointSpanChanged(HtmlConsts.STRING_CLASS, yychar);
        onNonSymbolMatched(yytext().substring(1, yylength()-1), yychar);
        onDisjointSpanChanged(lastClassName, yychar);
        onNonSymbolMatched("]", yychar);
        yypop();
    }

    \[ "$" {Identifier} \] {
        chkLOC();
        onNonSymbolMatched("[$", yychar);
        onFilteredSymbolMatched(yytext().substring(2, yylength()-1), yychar, null);
        onNonSymbolMatched("]", yychar);
        yypop();
    }

    "->" {Identifier} {
        chkLOC();
        onNonSymbolMatched(yytext().substring(0, 2), yychar);
        onFilteredSymbolMatched(yytext().substring(2), yychar, null);
        yypop(); //because "$arr->a[0]" is the same as $arr->a . "[0]"
    }

    [^]          { yypushback(1); yypop(); }
}

<STRINGEXPR> {
    {Identifier} {
        chkLOC();
        onFilteredSymbolMatched(yytext(), yychar, null);
    }
    \}  { chkLOC(); onNonSymbolMatched('}', yychar); yypop(); }
    \[  { chkLOC(); onNonSymbolMatched('[', yychar); yybegin(IN_SCRIPT); } /* don't push. when we find '}'
                                                 * and we pop we want to go to
                                                 * STRING/HEREDOC, not back to
                                                 * STRINGEXPR */
}

<SCOMMENT> {
    {ClosingTag}    {
        onDisjointSpanChanged(null, yychar);
        onNonSymbolMatched(yytext(), EmphasisHint.STRONG, yychar);
        while (!isHtmlState(yystate()))
            yypop();
    }
    {WhspChar}* {EOL} {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        yypop();
    }
}

<DOCCOMMENT> {
    {DocPreviousChar} "@" {DocParamWithType} {
        writeDocTag(); yybegin(DOCCOM_TYPE);
    }

    {DocPreviousChar} "@" {DocParamWithTypeAndName} {
        writeDocTag(); yybegin(DOCCOM_TYPE_THEN_NAME);
    }

    {DocPreviousChar} "@" {DocParamWithName} {
        writeDocTag(); yybegin(DOCCOM_NAME);
    }

    ("{@" {DocInlineTags}) | {DocPreviousChar} "@" {Identifier} {
        writeDocTag();
    }
}

<DOCCOM_TYPE_THEN_NAME, DOCCOM_TYPE> {
    {WhspChar}+ {DocType} {
        int i = 0;
        do { onNonSymbolMatched(yycharat(i++), yychar); } while (isTabOrSpace(i));
        int j = i;
        while (i < yylength()) {
            //skip over [], |, ( and )
            char c;
            while (i < yylength() && ((c = yycharat(i)) == '[' || c == ']'
                    || c == '|' || c == '(' || c == ')')) {
                onNonSymbolMatched(c, yychar);
                i++;
            }
            j = i;
            while (j < yylength() && (c = yycharat(j)) != ')' && c != '|'
            && c != '[') { j++; }
            onFilteredSymbolMatched(yytext().substring(i, j), yychar,
                    PSEUDO_TYPES, false);
            i = j;
        }
        yybegin(yystate() == DOCCOM_TYPE_THEN_NAME ? DOCCOM_NAME : DOCCOMMENT);
    }

    [^] { yybegin(DOCCOMMENT); yypushback(1); }
}

<DOCCOM_NAME> {
    {WhspChar}+ "$" {Identifier} {
        int i = 0;
        do { onNonSymbolMatched(yycharat(i++), yychar); } while (isTabOrSpace(i));

        onNonSymbolMatched("$", yychar);
        onFilteredSymbolMatched(yytext().substring(i + 1), yychar, null);
        yybegin(DOCCOMMENT);
    }

    [^] { yybegin(DOCCOMMENT); yypushback(1); }
}

<COMMENT, DOCCOMMENT> {
    {WhspChar}* {EOL} {
        onDisjointSpanChanged(null, yychar);
        onEndOfLineMatched(yytext(), yychar);
        onDisjointSpanChanged(HtmlConsts.COMMENT_CLASS, yychar);
    }
    "*/"    {
        onNonSymbolMatched(yytext(), yychar);
        onDisjointSpanChanged(null, yychar);
        yypop();
    }
}

<YYINITIAL, TAG_NAME, AFTER_TAG_NAME, ATTRIBUTE_NOQUOTE, ATTRIBUTE_DOUBLE,
    ATTRIBUTE_SINGLE, HTMLCOMMENT, IN_SCRIPT, STRING, QSTRING, BACKQUOTE,
    HEREDOC, NOWDOC, SCOMMENT, COMMENT, DOCCOMMENT, STRINGEXPR, STRINGVAR> {

    {WhspChar}* {EOL} {
        onEndOfLineMatched(yytext(), yychar);
    }
    [[\s]--[\n]]    { onNonSymbolMatched(yytext(), yychar); }
    [^\n]    { chkLOC(); onNonSymbolMatched(yytext(), yychar); }
}

<YYINITIAL, HTMLCOMMENT, SCOMMENT, COMMENT, DOCCOMMENT, STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC> {
    {FPath}
            { chkLOC(); onPathlikeMatched(yytext(), '/', false, yychar); }

    {File}
            {
            chkLOC();
            String path = yytext();
            onFilelikeMatched(path, yychar);
    }

    {BrowseableURI}    {
              chkLOC();
              onUriMatched(yytext(), yychar);
            }

    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
            {
            chkLOC();
            onEmailAddressMatched(yytext(), yychar);
            }
}
