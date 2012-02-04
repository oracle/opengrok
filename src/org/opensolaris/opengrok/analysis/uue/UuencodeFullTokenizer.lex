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
 * Copyright (c) 2012 Constantine A. Murenin <mureninc++@gmail.com>
 */

package org.opensolaris.opengrok.analysis.uue;
import java.io.IOException;
import java.io.Reader;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;


%%

%public
%class UuencodeFullTokenizer
%extends JFlexTokenizer
%unicode
%type boolean
%eofval{
return false;
%eofval}
%caseless
%char

//WhiteSpace     = [ \t\f\r]+|\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
Printable = [\@\$\%\^\&\-+=\?\.\:]

%state MODE NAME UUE

%%
<<EOF>>   { return false; }

<YYINITIAL> {
  ^ ( "begin " | "begin-base64 " ) {
    yybegin(MODE);
    yypushback(1);
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }

  {Identifier}|{Number}|{Printable} {
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }

  . {}
}

<MODE> {
  [ ] {}
  [^ \n]+ " " {
    yybegin(NAME);
    yypushback(1);
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }
  . { yybegin(YYINITIAL); yypushback(1); }
}

<NAME>{
  [ ] {}
  [^ \n]+\n {
    yybegin(UUE);
    yypushback(1);
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }
  . { yybegin(YYINITIAL); yypushback(1); }
}

<UUE> {
  ^ ( "end" | "====" ) \n {
    yybegin(YYINITIAL);
    yypushback(1);
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }
  . {}
}
