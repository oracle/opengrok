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
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a PHP file
 */

package org.opensolaris.opengrok.analysis.php;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class PhpXref
%extends JFlexXref
%unicode
%ignorecase
%int
%include CommonLexer.lexh
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
          disjointSpan(popString);
      }
      super.yypop();
  }

  private void writeDocTag() throws IOException {
    out.write(yycharat(0));
    out.append("<strong>").append(Util.htmlize(yytext().substring(1))).append("</strong>");
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

DocPreviousChar = "*" | {WhiteSpace}

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

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%%
<YYINITIAL> { //HTML
    "<" | "</"      { out.write(Util.htmlize(yytext())); yypush(TAG_NAME); }

    "<!--" {
        disjointSpan(HtmlConsts.COMMENT_CLASS);
        out.write(htmlize(yytext()));
        yybegin(HTMLCOMMENT);
    }
}

<TAG_NAME> {
    {HtmlName} {
        String lastClassName = getDisjointSpanClassName();
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        out.write(yytext());
        disjointSpan(lastClassName);
        yybegin(AFTER_TAG_NAME);
    }

    {HtmlName}:{HtmlName} {
        String lastClassName = getDisjointSpanClassName();
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        int i = 0;
        while (yycharat(i) != ':') i++;
        out.write(yytext().substring(0,i));
        disjointSpan(null);
        out.write(":");
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        out.write(yytext().substring(i + 1));
        disjointSpan(lastClassName);
        yybegin(AFTER_TAG_NAME);
    }
}

<AFTER_TAG_NAME> {
    {HtmlName} {
        //attribute
        out.append("<strong>").append(yytext()).append("</strong>");
    }

    "=" {WhspChar}* (\" | \')? {
        char attributeDelim = yycharat(yylength()-1);
        out.write("=");
        disjointSpan(HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext().substring(1)));
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
    ">"     { out.write(htmlize(yytext())); yypop(); } //to YYINITIAL
}

<YYINITIAL, TAG_NAME, AFTER_TAG_NAME> {
    {OpeningTag}    {
        out.append("<strong>").append(Util.htmlize(yytext())).append("</strong>");
        yypush(IN_SCRIPT); }
}

<ATTRIBUTE_NOQUOTE> {
    {WhspChar}* {EOL} {
        disjointSpan(null);
        startNewLine();
        yypop();
    }
    {WhiteSpace} {
        out.write(yytext());
        disjointSpan(null);
        yypop();
    }
    ">"     { out.write(htmlize(yytext())); disjointSpan(null); yypop(); yypop(); } //pop twice
}

<ATTRIBUTE_DOUBLE>\" { out.write(htmlize(yytext())); disjointSpan(null); yypop(); }
<ATTRIBUTE_SINGLE>\' { out.write(htmlize(yytext())); disjointSpan(null); yypop(); }

<ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE> {
    {WhspChar}* {EOL} {
        disjointSpan(null);
        startNewLine();
        disjointSpan(HtmlConsts.STRING_CLASS);
    }
}

<ATTRIBUTE_NOQUOTE, ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE> {
    {OpeningTag} {
        disjointSpan(null);
        out.append("<strong>").append(Util.htmlize(yytext())).append("</strong>");
        yypush(IN_SCRIPT, HtmlConsts.STRING_CLASS);
    }
}

<HTMLCOMMENT> {
    "-->" {
        out.write(htmlize(yytext()));
        disjointSpan(null);
        yybegin(YYINITIAL);
    }

    {WhspChar}* {EOL} {
        disjointSpan(null);
        startNewLine();
        disjointSpan(HtmlConsts.COMMENT_CLASS);
    }

    {OpeningTag} {
        disjointSpan(null);
        out.append("<strong>").append(Util.htmlize(yytext())).append("</strong>");
        yypush(IN_SCRIPT, HtmlConsts.COMMENT_CLASS);
    }
}

<IN_SCRIPT> {
    "$" {Identifier} {
        //we ignore keywords if the identifier starts with one of variable chars
        String id = yytext().substring(1);
        out.write("$");
        writeSymbol(id, null, yyline);
    }

    {Identifier} {
        writeSymbol(yytext(), Consts.kwd, yyline);
    }

    \( {WhspChar}* {CastTypes} {WhspChar}* \) {
        out.write("(");
        int i = 1, j;
        while (isTabOrSpace(i)) { out.write(yycharat(i++)); }

        j = i + 1;
        while (!isTabOrSpace(j) && yycharat(j) != ')') { j++; }
        out.append("<em>").append(yytext().substring(i, j)).append("</em>");

        out.write(yytext().substring(j, yylength()));
    }

    b? \" {
        yypush(STRING);
        if (yycharat(0) == 'b') { out.write('b'); }
        disjointSpan(HtmlConsts.STRING_CLASS);
        out.write(htmlize("\""));
    }

    b? \' {
        yypush(QSTRING);
        if (yycharat(0) == 'b') { out.write('b'); }
        disjointSpan(HtmlConsts.STRING_CLASS);
        out.write(htmlize("\'"));
    }

    [`]    {
        yypush(BACKQUOTE);
        disjointSpan(HtmlConsts.STRING_CLASS);
        out.write(yytext());
    }

    b? "<<<" {WhspChar}* ({Identifier} | (\'{Identifier}\') | (\"{Identifier}\")){EOL} {
        if (yycharat(0) == 'b') { out.write('b'); }
        out.write(htmlize("<<<"));
        int i = yycharat(0) == 'b' ? 4 : 3, j = yylength()-1;
        while (isTabOrSpace(i)) {
            out.write(yycharat(i++));
        }
        while (yycharat(j) == '\n' || yycharat(j) == '\r') { j--; }

        if (yycharat(i) == '\'' || yycharat(i) == '"') {
            yypush(NOWDOC);
            String text = yytext().substring(i+1, j);
            this.docLabels.push(text);
            out.write(htmlize(String.valueOf(yycharat(i))));
            disjointSpan(HtmlConsts.BOLD_CLASS);
            out.write(text);
            disjointSpan(null);
            out.write(htmlize(String.valueOf(yycharat(i))));
        } else {
            yypush(HEREDOC);
            String text = yytext().substring(i, j+1);
            this.docLabels.push(text);
            disjointSpan(HtmlConsts.BOLD_CLASS);
            out.write(text);
            disjointSpan(null);
        }
        startNewLine();
        disjointSpan(HtmlConsts.STRING_CLASS);
    }

    {Number}   {
        String lastClassName = getDisjointSpanClassName();
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        out.write(yytext());
        disjointSpan(lastClassName);
    }

    "#"|"//"   {
        yypush(SCOMMENT);
        disjointSpan(HtmlConsts.COMMENT_CLASS);
        out.write(yytext());
    }
    "/**"      {
        yypush(DOCCOMMENT);
        disjointSpan(HtmlConsts.COMMENT_CLASS);
        out.write("/*");
        yypushback(1);
    }
    "/*"       {
        yypush(COMMENT);
        disjointSpan(HtmlConsts.COMMENT_CLASS);
        out.write(yytext());
    }

    \{         { out.write(yytext()); yypush(IN_SCRIPT); }
    \}         {
        out.write(yytext());
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
        out.append("<strong>").append(Util.htmlize(yytext())).append("</strong>");
        while (!isHtmlState(yystate()))
            yypop();
    }
} //end of IN_SCRIPT

<STRING> {
    \\\" { out.append("<strong>").append(htmlize(yytext())).append("</strong>"); }
    \" { out.write(htmlize(yytext())); disjointSpan(null); yypop(); }
}

<BACKQUOTE> {
    "\\`" { out.append("<strong>").append(yytext()).append("</strong>"); }
    "`" { out.write("`"); disjointSpan(null); yypop(); }
}

<STRING, BACKQUOTE, HEREDOC> {
    "\\{" {
        out.write(yytext());
    }

    {DoubleQuoteEscapeSequences} {
        out.append("<strong>").append(htmlize(yytext())).append("</strong>");
    }

    "$"     {
        disjointSpan(null);
        out.write("$");
        yypush(STRINGVAR, HtmlConsts.STRING_CLASS);
    }

    "${" {
        disjointSpan(null);
        out.write(yytext());
        yypush(STRINGEXPR, HtmlConsts.STRING_CLASS);
    }

    /* ${ is different from {$ -- for instance {$foo->bar[1]} is valid
     * but ${foo->bar[1]} is not. ${ only enters the full blown scripting state
     * when {Identifer}[ is found (see the PHP scanner). Tthe parser seems to
     * put more restrictions on the {$ scripting mode than on the
     * "${" {Identifer} "[" scripting mode, but that's not relevant here */
    "{$" {
        disjointSpan(null);
        out.write("{");
        yypushback(1);
        yypush(IN_SCRIPT, HtmlConsts.STRING_CLASS);
    }
}

<QSTRING> {
    {SingleQuoteEscapeSequences} {
        out.append("<strong>").append(htmlize(yytext())).append("</strong>");
    }

    \'      { out.write(htmlize("'")); disjointSpan(null); yypop(); }
}

<HEREDOC, NOWDOC>^{Identifier} ";"? {EOL}  {
    int i = yylength() - 1;
    boolean hasSemi = false;
    while (yycharat(i) == '\n' || yycharat(i) == '\r') { i--; }
    if (yycharat(i) == ';') { hasSemi = true; i--; }
    if (yytext().substring(0, i+1).equals(this.docLabels.peek())) {
        String text = this.docLabels.pop();
        yypop();
        disjointSpan(HtmlConsts.BOLD_CLASS);
        out.write(text);
        disjointSpan(null);
        if (hasSemi) out.write(";");
        startNewLine();
    } else {
        out.write(yytext().substring(0,i+1));
        if (hasSemi) out.write(";");
        startNewLine();
    }
}

<STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC>{WhspChar}* {EOL} {
    disjointSpan(null);
    startNewLine();
    disjointSpan(HtmlConsts.STRING_CLASS);
}

<STRINGVAR> {
    {Identifier}    { writeSymbol(yytext(), null, yyline); }

    \[ {Number} \] {
        out.write("[");
        String lastClassName = getDisjointSpanClassName();
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        out.write(yytext().substring(1, yylength()-1));
        disjointSpan(lastClassName);
        out.write("]");
        yypop(); //because "$arr[0][1]" is the same as $arr[0] . "[1]"
    }

    \[ {Identifier} \] {
        //then the identifier is actually a string!
        out.write("[");
        String lastClassName = getDisjointSpanClassName();
        disjointSpan(HtmlConsts.STRING_CLASS);
        out.write(yytext().substring(1, yylength()-1));
        disjointSpan(lastClassName);
        out.write("]");
        yypop();
    }

    \[ "$" {Identifier} \] {
        out.write("[$");
        writeSymbol(yytext().substring(2, yylength()-1), null, yyline);
        out.write("]");
        yypop();
    }

    "->" {Identifier} {
        out.write(htmlize(yytext().substring(0, 2)));
        writeSymbol(yytext().substring(2), null, yyline);
        yypop(); //because "$arr->a[0]" is the same as $arr->a . "[0]"
    }

    [^]          { yypushback(1); yypop(); }
}

<STRINGEXPR> {
    {Identifier} {
        writeSymbol(yytext(), null, yyline);
    }
    \}  { out.write('}'); yypop(); }
    \[  { out.write('['); yybegin(IN_SCRIPT); } /* don't push. when we find '}'
                                                 * and we pop we want to go to
                                                 * STRING/HEREDOC, not back to
                                                 * STRINGEXPR */
}

<SCOMMENT> {
    {ClosingTag}    {
        disjointSpan(null);
        out.append("<strong>").append(Util.htmlize(yytext())).append("</strong>");
        while (!isHtmlState(yystate()))
            yypop();
    }
    {WhspChar}* {EOL} {
        disjointSpan(null);
        startNewLine();
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
    {WhiteSpace} {DocType} {
        int i = 0;
        do { out.write(yycharat(i++)); } while (isTabOrSpace(i));
        int j = i;
        while (i < yylength()) {
            //skip over [], |, ( and )
            char c;
            while (i < yylength() && ((c = yycharat(i)) == '[' || c == ']'
                    || c == '|' || c == '(' || c == ')')) {
                out.write(c);
                i++;
            }
            j = i;
            while (j < yylength() && (c = yycharat(j)) != ')' && c != '|'
            && c != '[') { j++; }
            writeSymbol(Util.htmlize(yytext().substring(i, j)),
                    PSEUDO_TYPES, yyline, false);
            i = j;
        }
        yybegin(yystate() == DOCCOM_TYPE_THEN_NAME ? DOCCOM_NAME : DOCCOMMENT);
    }

    [^] { yybegin(DOCCOMMENT); yypushback(1); }
}

<DOCCOM_NAME> {
    {WhiteSpace} "$" {Identifier} {
        int i = 0;
        do { out.write(yycharat(i++)); } while (isTabOrSpace(i));

        out.write("$");
        writeSymbol(Util.htmlize(yytext().substring(i + 1)), null, yyline);
        yybegin(DOCCOMMENT);
    }

    [^] { yybegin(DOCCOMMENT); yypushback(1); }
}

<COMMENT, DOCCOMMENT> {
    {WhspChar}* {EOL} {
        disjointSpan(null);
        startNewLine();
        disjointSpan(HtmlConsts.COMMENT_CLASS);
    }
    "*/"    {
        out.write(yytext());
        disjointSpan(null);
        yypop();
    }
}

<YYINITIAL, TAG_NAME, AFTER_TAG_NAME, ATTRIBUTE_NOQUOTE, ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE, HTMLCOMMENT, IN_SCRIPT, STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC, SCOMMENT, COMMENT, DOCCOMMENT, STRINGEXPR, STRINGVAR> {
    [&<>\'\"]    { out.write(htmlize(yytext())); }
    {WhspChar}* {EOL} {
        startNewLine();
    }
    {WhiteSpace}    {
        out.write(yytext());
    }
    [!-~]   { out.write(yytext()); }
    [^\n]       { writeUnicodeChar(yycharat(0)); }
}

<YYINITIAL, HTMLCOMMENT, SCOMMENT, COMMENT, DOCCOMMENT, STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC> {
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
