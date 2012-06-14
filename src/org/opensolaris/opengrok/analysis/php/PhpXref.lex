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
import java.util.Stack;

%%
%public
%class PhpXref
%extends JFlexXref
%unicode
%ignorecase
%int
%{
  private Stack<String> docLabels = new Stack<String>();
  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}
%debug

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

OpeningTag = ("<?" "php"?) | "<?="
ClosingTag = "?>"

HtmlNameStart = [:a-zA-Z_\u00C0-\u10FFFFFF]
HtmlName      = {HtmlNameStart} ({HtmlNameStart} | [\-.0-9\u00B7])*

%state TAG_NAME AFTER_TAG_NAME ATTRIBUTE_NOQUOTE ATTRIBUTE_SINGLE ATTRIBUTE_DOUBLE
%state IN_SCRIPT STRING SCOMMENT HEREDOC NOWDOC COMMENT QSTRING STRINGEXPR STRINGVAR

%%
<YYINITIAL> { //HTML
    "<" | "</"      { out.write(Util.htmlize(yytext())); yypush(TAG_NAME, null); }
}

<TAG_NAME> {
    {HtmlName} {
        out.write("<span class=\"b\">");
        writeSymbol(yytext(), null, yyline);
        out.write("</span>");
        yybegin(AFTER_TAG_NAME);
    }
}

<AFTER_TAG_NAME> {
    {HtmlName} {
        writeSymbol(yytext(), null, yyline);
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

    \"         { yypush(STRING, null); out.write("<span class=\"s\">\""); }
    \'         { yypush(QSTRING, null); out.write("<span class=\"s\">\'"); }

    "<<<" {WhiteSpace}* ({Identifier} | (\'{Identifier}\') | (\"{Identifier}\")){EOL} {
        out.write("&lt;&lt;&lt;");
        int i = 3, j = yylength()-1;
        while (yycharat(i) == ' ' || yycharat(i) == '\t') {
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
        out.write("<span class=\"c\">");
    }

    {Number}   { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

    "#"|"//"   { yypush(SCOMMENT, null); out.write("<span class=\"c\">" + yytext()); }
    "/*"       { yypush(COMMENT, null); out.write("<span class=\"c\">/*"); }

    \{         { out.write(yytext()); yypush(IN_SCRIPT, null); }
    \}         { out.write(yytext()); if (!this.stack.empty()) yypop(); } //may pop STRINGEXPR

    {ClosingTag}    { out.write(Util.htmlize(yytext())); yypop(); }
} //end of IN_SCRIPT

<STRING>\" { out.write("\"</span>"); yypop(); }

<STRING, HEREDOC> {
    \\\\ | \\\" | "\\{" | "\\$" {
        out.write(yytext());
    }

    "$"     {
        out.write("</span>$");
        yypush(STRINGVAR, "<span class=\"s\">");
    }

    "{$" | "${" {
        out.write("</span>");
        out.write(yytext());
        yypush(STRINGEXPR, "<span class=\"s\">");
    }
}

<QSTRING> {
    \\\\ | \\\' {
        out.write(yytext());
    }
    \'      { out.write("\"</span>"); yypop(); }
}

<HEREDOC, NOWDOC>^{Identifier} ";" {EOL}  {
    int i = yylength() - 1;
    while (yycharat(i) == '\n' || yycharat(i) == '\r') { i--; }
    if (yytext().substring(0, i).equals(this.docLabels.peek())) {
        String text = this.docLabels.pop();
        yypop();
        out.write("</span><span class=\"b\">");
        out.write(text);
        out.write("</span>;");
        startNewLine();
    } else {
        out.write(yytext().substring(0,i) + ";");
        startNewLine();
    }
}

<STRING, QSTRING, HEREDOC, NOWDOC>{WhiteSpace}* {EOL} {
    out.write("</span>;");
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
    \[  { out.write('['); yypush(IN_SCRIPT, null); }
}

<SCOMMENT> {
    {ClosingTag}    {
        out.write("</span>");
        out.write(Util.htmlize(yytext()));
        yypop(); yypop(); //hopefully pop to YYINITIAL
    }
    {WhiteSpace}* {EOL} {
        out.write("</span>");
        startNewLine();
        yypop();
    }
}

<COMMENT> {
    "*/"    { out.write("*/</span>"); yypop(); }
}

<YYINITIAL, TAG_NAME, AFTER_TAG_NAME, ATTRIBUTE_NOQUOTE, ATTRIBUTE_DOUBLE, ATTRIBUTE_SINGLE, IN_SCRIPT, STRING, QSTRING, HEREDOC, NOWDOC, SCOMMENT, COMMENT, STRINGEXPR, STRINGVAR> {
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

<YYINITIAL, SCOMMENT, COMMENT, QSTRING, STRING, HEREDOC, NOWDOC> {
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
