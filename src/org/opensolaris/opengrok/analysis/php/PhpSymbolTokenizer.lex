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
 * Gets Php symbols - ignores comments, strings, keywords
 */

package org.opensolaris.opengrok.analysis.php;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import java.util.*;

%%
%public
%class PhpSymbolTokenizer
%extends JFlexTokenizer
%unicode
%init{
super(in);
%init}
%type boolean
%eofval{
this.finalOffset=zzEndRead;
return false;
%eofval}
%char
%ignorecase
%{
  private final static Set<String> PSEUDO_TYPES;
  private Stack<String> docLabels = new Stack<String>();

  static {
    PSEUDO_TYPES = new HashSet<String>(Arrays.asList(
        new String[] {
            "string", "integer", "int", "boolean", "bool", "float", "double",
            "object", "mixed", "array", "resource", "void", "null", "callback",
            "false", "true", "self", "callable"
        }
    ));
  }

  private boolean isTabOrSpace(int i) {
    return yycharat(i) == '\t' || yycharat(i) == ' ';
  }

  private static boolean isHtmlState(int state) {
    return state == YYINITIAL;
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

OpeningTag = ("<?" "php"?) | "<?="
ClosingTag = "?>"

CastTypes = "int"|"integer"|"real"|"double"|"float"|"string"|"binary"|"array"
            |"object"|"bool"|"boolean"|"unset"

DoubleQuoteEscapeSequences = \\ (([nrtfve\\$]) | ([xX] [0-9a-fA-F]{1,2}) |  ([0-7]{1,3}))
SingleQuoteEscapeSequences = \\ [\\\']

DocPreviousChar = "*" | {WhiteSpace}

DocParamWithType = "return" | "throws" | "throw" | "var" | "see"  //"see" can take a URL
DocParamWithTypeAndName = "param" | "global" | "property" | "property-read"
                          | "property-write"
DocParamWithName = "uses"
//method needs special treatment

%state IN_SCRIPT STRING SCOMMENT HEREDOC NOWDOC COMMENT QSTRING BACKQUOTE STRINGEXPR STRINGVAR
%state DOCCOMMENT DOCCOM_TYPE_THEN_NAME DOCCOM_NAME DOCCOM_TYPE

%%

<YYINITIAL> {
    {OpeningTag}    { yypush(IN_SCRIPT); }
}


<IN_SCRIPT> {
    "$" {Identifier} {
        //we ignore keywords if the identifier starts with one of variable chars
        setAttribs(yytext().substring(1), yychar + 1, yychar + yylength());
        return true;
    }

    {Identifier} {
        if (!Consts.kwd.contains(yytext())) {
            setAttribs(yytext(), yychar, yychar + yylength());
            return true;
        }
    }

    \( {WhiteSpace}* {CastTypes} {WhiteSpace}* \) { }

    b? \" { yypush(STRING); }

    b? \' { yypush(QSTRING); }

    ` { yypush(BACKQUOTE); }

    b? "<<<" {WhiteSpace}* ({Identifier} | (\'{Identifier}\') | (\"{Identifier}\")){EOL} {
        int i = yycharat(0) == 'b' ? 4 : 3, j = yylength()-1;
        while (isTabOrSpace(i)) { i++; }
        while (yycharat(j) == '\n' || yycharat(j) == '\r') { j--; }

        if (yycharat(i) == '\'' || yycharat(i) == '"') {
            yypush(NOWDOC);
            String text = yytext().substring(i+1, j);
            this.docLabels.push(text);
        } else {
            yypush(HEREDOC);
            String text = yytext().substring(i, j+1);
            this.docLabels.push(text);
        }
    }

    {Number}   { }

    "#"|"//"   { yypush(SCOMMENT); }
    "/**"      { yypush(DOCCOMMENT); }
    "/*"       { yypush(COMMENT); }

    \{         { yypush(IN_SCRIPT); }
    \}         {
        if (!this.stack.empty() && !isHtmlState(this.stack.peek()))
            yypop(); //may pop STRINGEXPR/HEREDOC/BACKQUOTE
    }

    {ClosingTag}    { while (!isHtmlState(yystate())) yypop(); }
} //end of IN_SCRIPT

<STRING> {
    \\\" { }
    \" { yypop(); }
}

<BACKQUOTE> {
    "\\`" { }
    "`" { yypop(); }
}

<STRING, BACKQUOTE, HEREDOC> {
    "\\{" { }

    {DoubleQuoteEscapeSequences} {}

    "$"     { yypush(STRINGVAR); }

    "${"    { yypush(STRINGEXPR); }

    /* ${ is different from {$ -- for instance {$foo->bar[1]} is valid
     * but ${foo->bar[1]} is not. ${ only enters the full blown scripting state
     * when {Identifer}[ is found (see the PHP scanner). Tthe parser seems to
     * put more restrictions on the {$ scripting mode than on the
     * "${" {Identifer} "[" scripting mode, but that's not relevant here */
    "{$" {
        yypushback(1);
        yypush(IN_SCRIPT);
    }
}

<QSTRING> {
    {SingleQuoteEscapeSequences} { }
    \'      { yypop(); }
}

<HEREDOC, NOWDOC>^{Identifier} ";"? {EOL}  {
    int i = yylength() - 1;
    while (yycharat(i) == '\n' || yycharat(i) == '\r') { i--; }
    if (yycharat(i) == ';') { i--; }
    if (yytext().substring(0, i+1).equals(this.docLabels.peek())) {
        String text = this.docLabels.pop();
        yypop();
    }
}

<STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC>{WhiteSpace}* {EOL} { }

<STRINGVAR> {
    {Identifier} {
        setAttribs(yytext(), yychar, yychar + yylength());
        return true;
    }

    \[ {Number} \] {
        yypop(); //because "$arr[0][1]" is the same as $arr[0] . "[1]"
    }

    \[ {Identifier} \] {
        //then the identifier is actually a string!
        yypop();
    }

    \[ "$" {Identifier} \] {
        setAttribs(yytext().substring(2, yylength()-1), yychar + 2,
                yychar + yylength() - 1);
        yypop();
        return true;
    }

    "->" {Identifier} {
        setAttribs(yytext().substring(2), yychar + 2, yychar + yylength());
        yypop(); //because "$arr->a[0]" is the same as $arr->a . "[0]"
        return true;
    }

    . | \n          { yypushback(1); yypop(); }
}

<STRINGEXPR> {
    {Identifier} {
        setAttribs(yytext(), yychar, yychar + yylength());
        return true;
    }
    \}  { yypop(); }
    \[  { yybegin(IN_SCRIPT); } /* don't push. when we find '}'
                                                 * and we pop we want to go to
                                                 * STRING/HEREDOC, not back to
                                                 * STRINGEXPR */
}

<SCOMMENT> {
    {ClosingTag}    {
        while (!isHtmlState(yystate())) yypop();
    }
    {WhiteSpace}* {EOL} {
        yypop();
    }
}

<DOCCOMMENT> {
    /* change relatively to xref -- we also consume the whitespace after */
    {DocPreviousChar} "@" {DocParamWithType} {WhiteSpace}+ {
        yybegin(DOCCOM_TYPE);
    }

    {DocPreviousChar} "@" {DocParamWithTypeAndName} {WhiteSpace}+ {
        yybegin(DOCCOM_TYPE_THEN_NAME);
    }

    {DocPreviousChar} "@" {DocParamWithName} {WhiteSpace}+ {
        yybegin(DOCCOM_NAME);
    }
}

<DOCCOM_TYPE_THEN_NAME, DOCCOM_TYPE> {
    /* The rules here had to be substantially changed because we cannot find
     * several symbols in one match. This is substantially more lax than
     * the xref rules */

    [\[\]\|\(\)] { }

    {WhiteSpace} {
        yybegin(yystate() == DOCCOM_TYPE_THEN_NAME ? DOCCOM_NAME : DOCCOMMENT);
    }

    {Identifier} {
        if (!PSEUDO_TYPES.contains(yytext().toLowerCase())) {
            setAttribs(yytext(), yychar, yychar + yylength());
            return true;
        }
    }

    .|\n { yybegin(DOCCOMMENT); yypushback(1); }
}

<DOCCOM_NAME> {
    "$" {Identifier} {
        setAttribs(yytext().substring(1), yychar + 1, yychar + yylength());
        yybegin(DOCCOMMENT);
        return true;
    }

    .|\n { yybegin(DOCCOMMENT); yypushback(1); }
}

<COMMENT, DOCCOMMENT> {
    {WhiteSpace}* {EOL} {  }
    "*/"    { yypop(); }
}

<YYINITIAL, IN_SCRIPT, STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC, SCOMMENT, COMMENT, DOCCOMMENT, STRINGEXPR, STRINGVAR> {
    {WhiteSpace}* {EOL} { }
    {WhiteSpace}    { }
    [!-~]   { }
    .       { }
}

<YYINITIAL, SCOMMENT, COMMENT, DOCCOMMENT, STRING, QSTRING, BACKQUOTE, HEREDOC, NOWDOC> {
    {Path} { }

    {File} { }

    ("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
            { }

    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
            { }
}
