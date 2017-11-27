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
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a C++ file
 */

package org.opensolaris.opengrok.analysis.c;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class CxxXref
%extends JFlexXrefSimple
%unicode
%int
%include CommonXref.lexh
%{
  private static final Pattern MATCH_INCLUDE = Pattern.compile(
      "^(#.*)(include)(.*)([<\"])(.*)([>\"])$");
  private static final int INCL_HASH_G = 1;
  private static final int INCLUDE_G = 2;
  private static final int INCL_POST_G = 3;
  private static final int INCL_PUNC0_G = 4;
  private static final int INCL_PATH_G = 5;
  private static final int INCL_PUNCZ_G = 6;

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

File = [a-zA-Z]{FNameChar}* "." ([cChHsStT] | [Cc][Oo][Nn][Ff] |
    [Jj][Aa][Vv][Aa] | [CcHh][Pp][Pp] | [Cc][Cc] | [Tt][Xx][Tt] |
    [Hh][Tt][Mm][Ll]? | [Pp][Ll] | [Xx][Mm][Ll] | [CcHh][\+][\+] | [Hh][Hh] |
    [CcHh][Xx][Xx] | [Dd][Ii][Ff][Ff] | [Pp][Aa][Tt][Cc][Hh])

%state  STRING COMMENT SCOMMENT QSTRING

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Cxx.lexh
%%
<YYINITIAL>{
 \{     { incScope(); writeUnicodeChar(yycharat(0)); }
 \}     { decScope(); writeUnicodeChar(yycharat(0)); }
 \;     { endScope(); writeUnicodeChar(yycharat(0)); }

{Identifier} {
    String id = yytext();
    writeSymbol(id, CxxConsts.kwd, yyline);
}

"#" {WhspChar}* "include" {WhspChar}* ("<"[^>\n\r]+">" | \"[^\"\n\r]+\")    {
        String capture = yytext();
        Matcher match = MATCH_INCLUDE.matcher(capture);
        if (match.matches()) {
            out.write(match.group(INCL_HASH_G));
            writeSymbol(match.group(INCLUDE_G), CxxConsts.kwd, yyline);
            out.write(match.group(INCL_POST_G));
            out.write(htmlize(match.group(INCL_PUNC0_G)));
            String path = match.group(INCL_PATH_G);
            out.write(Util.breadcrumbPath(urlPrefix + "path=", path));
            out.write(htmlize(match.group(INCL_PUNCZ_G)));
        } else {
            out.write(htmlize(capture));
        }
}

/*{Hier}
        { out.write(Util.breadcrumbPath(urlPrefix+"defs=",yytext(),'.'));}
*/
{Number} {
    disjointSpan(HtmlConsts.NUMBER_CLASS);
    out.write(yytext());
    disjointSpan(null);
 }

 \"     {
    pushSpan(STRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 \'     {
    pushSpan(QSTRING, HtmlConsts.STRING_CLASS);
    out.write(htmlize(yytext()));
 }
 "/*"   {
    pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
 }
 "//"   {
    pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
    out.write(yytext());
 }
}

<STRING> {
 \\[\"\\] |
 \" {WhiteSpace} \"  { out.write(htmlize(yytext())); }
 \"      { out.write(htmlize(yytext())); yypop(); }
}

<QSTRING> {
 \\[\'\\] |
 \' {WhiteSpace} \' { out.write(htmlize(yytext())); }

 \'     { out.write(htmlize(yytext())); yypop(); }
}

<COMMENT> {
"*/"    { out.write(yytext()); yypop(); }
}

<SCOMMENT> {
{WhspChar}*{EOL}      {
    yypop();
    startNewLine();}
}


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
[&<>\'\"]    { out.write(htmlize(yytext())); }
{WhspChar}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 [^\n]      { writeUnicodeChar(yycharat(0)); }
}

<STRING, COMMENT, SCOMMENT, QSTRING> {
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

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}

<STRING, SCOMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<COMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.END_C_COMMENT);
    }
}

<QSTRING> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.APOS_NO_BSESC);
    }
}
