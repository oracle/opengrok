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
 * Cross reference a PHP file
 */

package org.opensolaris.opengrok.analysis.php;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;
import java.util.*;

%%
%public
%class PhpXref
%extends JFlexXref
%unicode
%ignorecase
%int
%{
  private final static Set<String> PSEUDO_TYPES;
  private Stack<String> docLabels = new Stack<String>();
  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }

  static {
    PSEUDO_TYPES = new HashSet<String>(Arrays.asList(
        new String[] {
            "string", "integer", "int", "boolean", "bool", "float", "double",
            "object", "mixed", "array", "resource", "void", "null", "callback",
            "false", "true", "self", "callable"
        }
    ));
  }

  private void writeDocTag() throws IOException {
    out.write(yycharat(0));
    out.write("<strong>");
    out.write(Util.htmlize(yytext().substring(1)));
    out.write("</strong>");
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

WhiteSpace     = [ \t]+
EOL = \r|\n|\r\n
Identifier = [a-zA-Z_\u007F-\u10FFFF] [a-zA-Z0-9_\u007F-\u10FFFF]*

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = [a-zA-Z]{FNameChar}* "." ("php"|"php3"|"php4"|"phps"|"phtml"|"inc"|"diff"|"patch")
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

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

HtmlNameStart = [:a-zA-Z_\u00C0-\u10FFFFFF]
HtmlName      = {HtmlNameStart} ({HtmlNameStart} | [\-.0-9\u00B7])*

%state TAG_NAME AFTER_TAG_NAME ATTRIBUTE_NOQUOTE ATTRIBUTE_SINGLE ATTRIBUTE_DOUBLE HTMLCOMMENT
%state IN_SCRIPT STRING SCOMMENT HEREDOC NOWDOC COMMENT QSTRING BACKQUOTE STRINGEXPR STRINGVAR
%state DOCCOMMENT DOCCOM_TYPE_THEN_NAME DOCCOM_NAME DOCCOM_TYPE

%%
<YYINITIAL> { //HTML
    "<" | "</"      { out.write(Util.htmlize(yytext())); yypush(TAG_NAME, null); }

    "<!--" {
        out.write("<span class=\"c\">&lt;!--");
        yybegin(HTMLCOMMENT);
    }
}

<TAG_NAME> {
    {HtmlName} {
        out.write("<span class=\"b\">");
        out.write(yytext());
        out.write("</span>");
        yybegin(AFTER_TAG_NAME);
    }
}

<AFTER_TAG_NAME> {
    {HtmlName} {
        out.write(yytext()); //attribute
    }

    "=" {WhiteSpace}* (\" | \')? {
        char attributeDelim = yycharat(yylength()-1);
        out.write("=<span class=\"s\">");
        out.write(yytext().substring(1));
        if (attributeDelim == '\'') {
            yypush(ATTRIBUTE_SINGLE, null);
        } else if (attributeDelim == '"') {
            yypush(ATTRIBUTE_DOUBLE, null);
        } else {
            yypush(ATTRIBUTE_NOQUOTE, null);
        }
    }
}

<TAG_NAME, AFTER_TAG_NAME> {
    ">"     { out.write("&gt;"); yypop(); } //to YYINITIAL
}

<YYINITIAL, TAG_NAME, AFTER_TAG_NAME> {
    {OpeningTag}    { out.write(Util.htmlize(yytext())); yypush(IN_SCRIPT, null); }
}

<ATTRIBUTE_NOQUOTE> {
    {WhiteSpace}* {EOL} {
        out.write("</span>");
        startNewLine();
        yypop();
    }
    {WhiteSpace} {
        out.write(yytext());
        out.write("</span>");
        yypop();
    }
    ">"     { out.write("&gt;</span>"); yypop(); yypop(); } //pop twice
}

<ATTRIBUTE_DOUBLE>\" { out.write("\"</span>"); yypop(); }
<ATTRIBUTE_SINGLE>\' { out.write("'</span>"); yypop(); }

<ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE> {
    {WhiteSpace}* {EOL} {
        out.write("</span>");
        startNewLine();
        out.write("<span class=\"s\">");
    }
}

<ATTRIBUTE_NOQUOTE, ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE> {
    {OpeningTag} {
        out.write("</span>");
        out.write(Util.htmlize(yytext()));
        yypush(IN_SCRIPT, "<span class=\"s\">");
    }
}

<HTMLCOMMENT> {
    "-->" {
        out.write("--&gt;</span>");
        yybegin(YYINITIAL);
    }

    {WhiteSpace}* {EOL} {
        out.write("</span>");
        startNewLine();
        out.write("<span class=\"c\">");
    }

    {OpeningTag} {
        out.write("</span>");
        out.write(Util.htmlize(yytext()));
        yypush(IN_SCRIPT, "<span class=\"c\">");
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

    \( {WhiteSpace}* {CastTypes} {WhiteSpace}* \) {
        out.write("(");
        int i = 1, j;
        while (isTabOrSpace(i)) { out.write(yycharat(i++)); }

        out.write("<em>");
        j = i + 1;
        while (!isTabOrSpace(j) && yycharat(j) != ')') { j++; }
        out.write(yytext().substring(i, j));
        out.write("</em>");

        out.write(yytext().substring(j, yylength()));
    }

    b? \" {
        yypush(STRING, null);
        if (yycharat(0) == 'b') { out.write('b'); }
        out.write("<span class=\"s\">\"");
    }

    b? \' {
        yypush(QSTRING, null);
        if (yycharat(0) == 'b') { out.write('b'); }
        out.write("<span class=\"s\">\'");
    }

    ` { yypush(BACKQUOTE, null); out.write("<span class=\"s\">`"); }

    b? "<<<" {WhiteSpace}* ({Identifier} | (\'{Identifier}\') | (\"{Identifier}\")){EOL} {
        if (yycharat(0) == 'b') { out.write('b'); }
        out.write("&lt;&lt;&lt;");
        int i = yycharat(0) == 'b' ? 4 : 3, j = yylength()-1;
        while (isTabOrSpace(i)) {
            out.write(yycharat(i++));
        }
        while (yycharat(j) == '\n' || yycharat(j) == '\r') { j--; }

        if (yycharat(i) == '\'' || yycharat(i) == '"') {
            yypush(NOWDOC, null);
            String text = yytext().substring(i+1, j);
            this.docLabels.push(text);
            out.write(yycharat(i));
            out.write("<span class=\"b\">");
            out.write(text);
            out.write("</span>");
            out.write(yycharat(i));
        } else {
            yypush(HEREDOC, null);
            String text = yytext().substring(i, j+1);
            this.docLabels.push(text);
            out.write("<span class=\"b\">");
            out.write(text);
            out.write("</span>");
        }
        startNewLine();
        out.write("<span class=\"s\">");
    }

    {Number}   { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

    "#"|"//"   { yypush(SCOMMENT, null); out.write("<span class=\"c\">" + yytext()); }
    "/**"      { yypush(DOCCOMMENT, null); out.write("<span class=\"c\">/*"); yypushback(1); }
    "/*"       { yypush(COMMENT, null); out.write("<span class=\"c\">/*"); }

    \{         { out.write(yytext()); yypush(IN_SCRIPT, null); }
    \}         {
        out.write(yytext());
        if (!this.stack.empty() && !isHtmlState(this.stack.peek()))
            yypop(); //may pop STRINGEXPR/HEREDOC/BACKQUOTE
        /* we don't pop unconditionally because we can exit a <?php block with
         * with open braces and we discard the information about the number of
         * open braces when exiting the block (see the action for {ClosingTag}
         * below. An alternative would be keeping two stacks -- one for HTML
         * and another for PHP. The PHP scanner only needs one stack because
         * it doesn't need to keep state about the HTML */
    }

    {ClosingTag} {
        out.write(Util.htmlize(yytext()));
        while (!isHtmlState(yystate()))
            yypop();
    }
} //end of IN_SCRIPT

<STRING> {
    \\\" { out.write("<strong>"); out.write(yytext()); out.write("</strong>"); }
    \" { out.write("\"</span>"); yypop(); }
}

<BACKQUOTE> {
    "\\`" { out.write("<strong>"); out.write(yytext()); out.write("</strong>"); }
    "`" { out.write("`</span>"); yypop(); }
}

<STRING, BACKQUOTE, HEREDOC> {
    "\\{" {
        out.write(yytext());
    }

    {DoubleQuoteEscapeSequences} {
        out.write("<strong>");
        out.write(yytext());
        out.write("</strong>");
    }

    "$"     {
        out.write("</span>$");
        yypush(STRINGVAR, "<span class=\"s\">");
    }

    "${" {
        out.write("</span>");
        out.write(yytext());
        yypush(STRINGEXPR, "<span class=\"s\">");
    }

    /* ${ is different from {$ -- for instance {$foo->bar[1]} is valid
     * but ${foo->bar[1]} is not. ${ only enters the full blown scripting state
     * when {Identifer}[ is found (see the PHP scanner). Tthe parser seems to
     * put more restrictions on the {$ scripting mode than on the
     * "${" {Identifer} "[" scripting mode, but that's not relevant here */
    "{$" {
        out.write("</span>");
        out.write("{");
        yypushback(1);
        yypush(IN_SCRIPT, "<span class=\"s\">");
    }
}

<QSTRING> {
    {SingleQuoteEscapeSequences} {
        out.write("<strong>");
        out.write(yytext());
        out.write("</strong>");
    }

    \'      { out.write("\"</span>"); yypop(); }
}

<HEREDOC, NOWDOC>^{Identifier} ";"? {EOL}  {
    int i = yylength() - 1;
    boolean hasSemi = false;
    while (yycharat(i) == '\n' || yycharat(i) == '\r') { i--; }
    if (yycharat(i) == ';') { hasSemi = true; i--; }
    if (yytext().substring(0, i+1).equals(this.docLabels.peek())) {
        String text = this.docLabels.pop();
        yypop();
        out.write("</span><span class=\"b\">");
        out.write(text);
        out.write("</span>");
        if (hasSemi) out.write(";");
        startNewLine();
    } else {
        out.write(yytext().substring(0,i+1));
        if (hasSemi) out.write(";");
        startNewLine();
    }
}

<STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC>{WhiteSpace}* {EOL} {
    out.write("</span>");
    startNewLine();
    out.write("<span class=\"s\">");
}

<STRINGVAR> {
    {Identifier}    { writeSymbol(yytext(), null, yyline); }

    \[ {Number} \] {
        out.write("[<span class=\"n\">");
        out.write(yytext().substring(1, yylength()-1));
        out.write("</span>]");
        yypop(); //because "$arr[0][1]" is the same as $arr[0] . "[1]"
    }

    \[ {Identifier} \] {
        //then the identifier is actually a string!
        out.write("[<span class=\"s\">");
        out.write(yytext().substring(1, yylength()-1));
        out.write("</span>]");
        yypop();
    }

    \[ "$" {Identifier} \] {
        out.write("[$");
        writeSymbol(yytext().substring(2, yylength()-1), null, yyline);
        out.write("]");
        yypop();
    }

    "->" {Identifier} {
        out.write("-&gt;");
        writeSymbol(yytext().substring(2), null, yyline);
        yypop(); //because "$arr->a[0]" is the same as $arr->a . "[0]"
    }

    . | \n          { yypushback(1); yypop(); }
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
        out.write("</span>");
        out.write(Util.htmlize(yytext()));
        while (!isHtmlState(yystate()))
            yypop();
    }
    {WhiteSpace}* {EOL} {
        out.write("</span>");
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
    {WhiteSpace}+ {DocType} {
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
            out.write("<em>");
            writeSymbol(Util.htmlize(yytext().substring(i, j)),
                    PSEUDO_TYPES, yyline, false);
            out.write("</em>");
            i = j;
        }
        yybegin(yystate() == DOCCOM_TYPE_THEN_NAME ? DOCCOM_NAME : DOCCOMMENT);
    }

    .|\n { yybegin(DOCCOMMENT); yypushback(1); }
}

<DOCCOM_NAME> {
    {WhiteSpace}+ "$" {Identifier} {
        int i = 0;
        do { out.write(yycharat(i++)); } while (isTabOrSpace(i));

        out.write("<em>$");
        writeSymbol(Util.htmlize(yytext().substring(i + 1)), null, yyline);
        out.write("</em>");
        yybegin(DOCCOMMENT);
    }

    .|\n { yybegin(DOCCOMMENT); yypushback(1); }
}

<COMMENT, DOCCOMMENT> {
    {WhiteSpace}* {EOL} {
        out.write("</span>");
        startNewLine();
        out.write("<span class=\"c\">");
    }
    "*/"    { out.write("*/</span>"); yypop(); }
}

<YYINITIAL, TAG_NAME, AFTER_TAG_NAME, ATTRIBUTE_NOQUOTE, ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE, HTMLCOMMENT, IN_SCRIPT, STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC, SCOMMENT, COMMENT, DOCCOMMENT, STRINGEXPR, STRINGVAR> {
    "&"     { out.write( "&amp;"); }
    "<"     { out.write( "&lt;"); }
    ">"     { out.write( "&gt;"); }
    {WhiteSpace}* {EOL} {
        startNewLine();
    }
    {WhiteSpace}    {
        out.write(yytext());
    }
    [!-~]   { out.write(yycharat(0)); }
    .       { writeUnicodeChar(yycharat(0)); }
}

<YYINITIAL, HTMLCOMMENT, SCOMMENT, COMMENT, DOCCOMMENT, STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC> {
    {Path}
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

    ("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
            {
            String url = yytext();
            out.write("<a href=\"");
            out.write(url);out.write("\">");
            out.write(url);out.write("</a>");}

    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
            {
            writeEMailAddress(yytext());
            }
}
