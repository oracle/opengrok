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
import java.io.IOException;
import java.io.Reader;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;

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
return false;
%eofval}
%char
%debug

Identifier = [a-zA-Z_\u007F-\u10FFFF] [a-zA-Z0-9_\u007F-\u10FFFF]*

%state STRING SCOMMENT COMMENT QSTRING STRINGEXPR STRINGVAR

%%

<YYINITIAL> {
{Identifier} {String id = yytext();
                if(!Consts.kwd.contains(id)){
                        setAttribs(id, yychar, yychar + yylength());
                        return true; }
              }
 \"     { yypush(STRING); }
 \'     { yypush(QSTRING); }
 "#"|"//" { yybegin(SCOMMENT); }
 "/*" { yybegin(COMMENT); }
}

<STRING> {
 \"     { yypop(); }
 \\\\   {}
 \\\"   {}
 "\\{"  {}
 "$"    { yypush(STRINGVAR); }
 "{$"   { yypush(STRINGEXPR); }
}

<QSTRING> {
 \'     { yypop(); }
 \\\'   {}
}

<STRINGVAR, STRINGEXPR> {
 {Identifier}   {
                    String id = yytext();
                    setAttribs(id, yychar, yychar + yylength());
                    return true;
                }
}

<STRINGVAR> {
 \[[^\]]+\]     {} //stuff between [] are strings, even though they're unquoted
 .|\n           {
                    yypushback(1); // don't consume it
                    yypop();
                }
 <<EOF>>   { return false;}
}

<STRINGEXPR> {
 {Identifier}   {
                    String id = yytext();
                    setAttribs(id, yychar, yychar + yylength());
                    return true;
                }
 \"             { yypush(STRING); }
 \'             { yypush(QSTRING); }
 \}             { yypop(); }
}

<SCOMMENT> {
 "?>"|\n    { yybegin(YYINITIAL);}
}

<COMMENT> {
 "*/"       { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, STRINGEXPR, SCOMMENT, COMMENT, QSTRING> {
<<EOF>>   { return false;}
.|\n    {}
}
