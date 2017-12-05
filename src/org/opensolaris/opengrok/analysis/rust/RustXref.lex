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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2016 Nikolay Denev.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Rust file
 */

package org.opensolaris.opengrok.analysis.rust;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;
%%
%public
%class RustXref
%extends JFlexXrefSimple
%unicode
%int
%include CommonXref.lexh
%{
  /**
   * Stores the number of hashes beginning and ending a raw string or raw byte
   * string. E.g., r##"blah"## has rawHashCount == 2.
   */
  int rawHashCount;

  int nestedComment;

  @Override
  public void reset() {
      super.reset();
      rawHashCount = 0;
      nestedComment = 0;
  }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

File = [a-zA-Z]{FNameChar}* "." ([Rr][Ss] | [Cc][Oo][Nn][Ff] | [Tt][Xx][Tt] |
    [Hh][Tt][Mm][Ll]? | [Xx][Mm][Ll] | [Ii][Nn][Ii] | [Dd][Ii][Ff][Ff] |
    [Pp][Aa][Tt][Cc][Hh])

%state  STRING RSTRING COMMENT SCOMMENT

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Rust.lexh
%%
<YYINITIAL> {
    \{ { incScope(); writeUnicodeChar(yycharat(0)); }
    \} { decScope(); writeUnicodeChar(yycharat(0)); }
    \; { endScope(); writeUnicodeChar(yycharat(0)); }
    {Identifier} {
        String id = yytext();
        writeSymbol(id, Consts.kwd, yyline);
    }
    "<" ({File}|{FPath}) ">" {
        out.write("&lt;");
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
        out.write("&gt;");
    }
    {Number} {
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        out.write(yytext());
        disjointSpan(null);
    }
    [b]?\" {
        pushSpan(STRING, HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
    }
    [b]?[r][#]*\" {
        pushSpan(RSTRING, HtmlConsts.STRING_CLASS);
        String capture = yytext();
        out.write(htmlize(capture));
        rawHashCount = RustUtils.countRawHashes(capture);
    }
    [b]?\' ([^\n\r\'\\] | \\[^\n\r]) \' |
    [b]?\' \\[xX]{HEXDIG}{HEXDIG} \' |
    [b]?\' \\[uU]\{ {HEXDIG}{1,6} \}\'    {
        disjointSpan(HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
        disjointSpan(null);
    }
    "//" {
        pushSpan(SCOMMENT, HtmlConsts.COMMENT_CLASS);
        out.write(yytext());
    }
    "/*"  {
        ++nestedComment;
        pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
        out.write(yytext());
    }
}

<STRING> {
    \\[\"\\]    { out.write(htmlize(yytext())); }
    \"    {
        out.write(htmlize(yytext()));
        yypop();
    }
}

<RSTRING> {
    \"[#]*    {
        String capture = yytext();
        if (RustUtils.isRawEnding(capture, rawHashCount)) {
            String ender = capture.substring(0, 1 + rawHashCount);
            out.write(htmlize(ender));
            yypop();
            int excess = capture.length() - ender.length();
            if (excess > 0) yypushback(excess);
        } else {
            out.write(htmlize(capture));
        }
    }
}

<STRING, RSTRING> {
    {WhspChar}*{EOL}    {
        disjointSpan(null);
        startNewLine();
        disjointSpan(HtmlConsts.STRING_CLASS);
    }
}

<COMMENT> {
    "*/"    {
        out.write(yytext());
        if (--nestedComment == 0) yypop();
    }
    "/*"    {
        ++nestedComment;
        out.write(yytext());
    }
}

<SCOMMENT> {
    {WhspChar}*{EOL} {
        yypop();
        startNewLine();
    }
}

<YYINITIAL, STRING, RSTRING, COMMENT, SCOMMENT> {
    [&<>\'\"]    { out.write(htmlize(yytext())); }
    {WhspChar}*{EOL} { startNewLine(); }
    {WhiteSpace} { out.write(yytext()); }
    [!-~] { out.write(yycharat(0)); }
    [^\n] { writeUnicodeChar(yycharat(0)); }
}

<STRING, SCOMMENT> {
    {FPath} { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/')); }

    {File} {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
    }

    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+ {
        writeEMailAddress(yytext());
    }
}

<STRING, RSTRING, SCOMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<COMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true, StringUtils.END_C_COMMENT);
    }
}
