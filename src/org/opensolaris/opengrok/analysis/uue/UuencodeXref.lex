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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2013 Constantine A. Murenin <C++@Cns.SU>
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.uue;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class UuencodeXref
%extends JFlexXref
%unicode
%int
%include CommonXref.lexh
%{ 

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
  
%}

%eof{
%eof}

%state MODE NAME UUE

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%%
<YYINITIAL> {
  ^ ( "begin " | "begin-base64 " ) {
    yybegin(MODE);
    yypushback(1);
    out.write("<strong>" + yytext() + "</strong>");
  }

  {BrowseableURI}    {
    appendLink(yytext(), true);
  }

  {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+ { writeEMailAddress(yytext()); }

  {FNameChar}+ { out.write(yytext()); }

  "<"     {out.write( "&lt;");}
  "&"     {out.write( "&amp;");}
  {EOL}   {startNewLine();}
  {WhiteSpace}   { out.write(yytext()); }
  [!-~]   { out.write(yycharat(0)); }
  [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<MODE> {
  [ ] { out.write(yycharat(0)); }
  [^ \n]+ " " {
    yybegin(NAME);
    yypushback(1);
    out.write("<i>" + yytext() + "</i>");
  }
  [^] { yybegin(YYINITIAL); yypushback(1); }
}

<NAME>{
  [ ] { out.write(yycharat(0)); }
  [^ \n]+\n {
    yybegin(UUE);
    yypushback(1);
    String t = yytext();
    out.write("<a href=\"" + urlPrefix + "q=" +
	      t.replaceAll("\"", "&quot;").replaceAll("&", "&amp;"));
    appendProject();
    out.write("\">" + t + "</a>");
    out.write("<span class='c'>");
  }
  [^] { yybegin(YYINITIAL); yypushback(1); }
}

<UUE> {
  ^ ( "end" | "====" ) \n {
    yybegin(YYINITIAL);
    yypushback(1);
    out.write("</span>" + "<strong>" + yytext() + "</strong>");
  }

  "<"     {out.write( "&lt;");}
  "&"     {out.write( "&amp;");}
  {EOL}   {startNewLine();}
  [^\n&<]+   { out.write(yytext()); }
}
