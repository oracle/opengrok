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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2013 Constantine A. Murenin <C++@Cns.SU>
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.uue;

import java.util.Locale;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
%%
%public
%class UuencodeFullTokenizer
%extends JFlexSymbolMatcher
%unicode
%int
%include ../CommonLexer.lexh
%caseless
%char
%{
  boolean b64;
  boolean modeFound;
  boolean nameFound;

  /**
   * Resets the uuencode tracked state after
   * {@link JFlexSymbolMatcher#reset()}.
   */
  @Override
  public void reset() {
      super.reset();
      b64 = false;
      modeFound = false;
      nameFound = false;
  }
%}

//WhiteSpace     = [ \t\f\r]+|\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
Printable = [\@\$\%\^\&\-+=\?\.\:]

%state BEGIN MODE NAME UUE

%%

<YYINITIAL> {
  ^ ( "begin " | "begin-" ) {
    b64 = false;
    modeFound = false;
    nameFound = false;
    yybegin(BEGIN);
    yypushback(1);
    onSymbolMatched(yytext().toLowerCase(Locale.ROOT), yychar);
    return yystate();
  }

  {Identifier}|{Number}|{Printable} {
    onSymbolMatched(yytext().toLowerCase(Locale.ROOT), yychar);
    return yystate();
  }

  [^] {}
}

<BEGIN> {
  " " {
    if (!b64)
      yybegin(MODE);
    else
      yybegin(YYINITIAL);
  }
  "-" {
    if (b64)
      yybegin(YYINITIAL);
    b64 = true;
    onSymbolMatched(yytext(), yychar);
    return yystate();
  }
  "base64 " {
    if (b64)
      yybegin(MODE);
    else
      yybegin(YYINITIAL);
    yypushback(1);
    onSymbolMatched(yytext().toLowerCase(Locale.ROOT), yychar);
    return yystate();
  }
  [^] { yybegin(YYINITIAL); yypushback(1); }
}

<MODE> {
  " " { if (modeFound) yybegin(NAME); }
  {Identifier}|{Number}|{Printable} {
    modeFound = true;
    onSymbolMatched(yytext().toLowerCase(Locale.ROOT), yychar);
    return yystate();
  }
  [^] { yybegin(YYINITIAL); yypushback(1); }
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
    onSymbolMatched(yytext().toLowerCase(Locale.ROOT), yychar);
    return yystate();
  }
  [^\n] { yybegin(YYINITIAL); yypushback(1); }
}

<UUE> {
  ^ ( "end" | "====" ) \n {
    yypushback(1);
    String t = yytext();
    if (t.equals("end") && !b64) {
      yybegin(YYINITIAL);
      onSymbolMatched(yytext().toLowerCase(Locale.ROOT), yychar);
      return yystate();
    } else if (t.equals("====") && b64)
      yybegin(YYINITIAL);
  }
[^]    {}
}
