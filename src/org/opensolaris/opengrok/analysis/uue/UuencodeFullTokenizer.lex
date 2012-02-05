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
%{
  boolean modeFound;
  boolean nameFound;
  boolean b64;
%}

//WhiteSpace     = [ \t\f\r]+|\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
Printable = [\@\$\%\^\&\-+=\?\.\:]

%state BEGIN MODE NAME UUE

%%
<<EOF>>   { return false; }

<YYINITIAL> {
  ^ ( "begin " | "begin-" ) {
    modeFound = false;
    nameFound = false;
    yybegin(BEGIN);
    yypushback(1);
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }

  {Identifier}|{Number}|{Printable} {
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }

  .|\n {}
}

<BEGIN> {
  " " {
    yybegin(MODE);
    b64 = false;
  }
  "-base64 " {
    yybegin(MODE);
    b64 = true;
    setAttribs("-",
	       yychar, yychar + 1);
    setAttribs(yytext().toLowerCase().substring(1, yylength() - 1),
	       yychar + 1, yychar + 1 + yylength() - 1);
    return true;
  }
  .|\n { yybegin(YYINITIAL); yypushback(1); }
}

<MODE> {
  " " { if (modeFound) yybegin(NAME); }
  {Identifier}|{Number}|{Printable} {
    modeFound = true;
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }
  .|\n { yybegin(YYINITIAL); yypushback(1); }
}

<NAME>{
  \n {
    if (nameFound)
      yybegin(UUE);
    else
      yybegin(YYINITIAL);
  }
  {Identifier}|{Number}|{Printable} {
    nameFound = true;
    setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
    return true;
  }
  . { yybegin(YYINITIAL); yypushback(1); }
}

<UUE> {
  ^ ( "end" | "====" ) \n {
    yypushback(1);
    String t = yytext();
    if (t.equals("end") && !b64) {
      yybegin(YYINITIAL);
      setAttribs(yytext().toLowerCase(), yychar, yychar + yylength());
      return true;
    } else if (t.equals("====") && b64)
      yybegin(YYINITIAL);
  }
  [ -~]* {}
  .|\n {}
}
