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
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Haskell file
 */

package org.opensolaris.opengrok.analysis.haskell;

import org.opensolaris.opengrok.analysis.JFlexXrefSimple;
import org.opensolaris.opengrok.web.HtmlConsts;
import org.opensolaris.opengrok.web.Util;

/**
 * @author Harry Pan
 */
%%
%public
%class HaskellXref
%extends JFlexXrefSimple
%unicode
%int
%include CommonXref.lexh
%{
    private int nestedComment;

    @Override
    public void reset() {
        super.reset();
        nestedComment = 0;
    }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

%state STRING CHAR COMMENT BCOMMENT

%include Common.lexh
%include CommonURI.lexh
%include CommonPath.lexh
%include Haskell.lexh
%%
<YYINITIAL> {
    {Identifier} {
        String id = yytext();
        writeSymbol(id, Consts.kwd, yyline);
    }
    {Number}     {
        disjointSpan(HtmlConsts.NUMBER_CLASS);
        out.write(yytext());
        disjointSpan(null);
    }
    \"           {
        pushSpan(STRING, HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
    }
    \'           {
        pushSpan(CHAR, HtmlConsts.STRING_CLASS);
        out.write(htmlize(yytext()));
    }
    "--"         {
        pushSpan(COMMENT, HtmlConsts.COMMENT_CLASS);
        out.write(yytext());
    }

    {NotComments}    { out.write(yytext()); }
}

<STRING> {
    \\[\"\\]    { out.write(htmlize(yytext())); }
    \"          {
        out.write(htmlize(yytext()));
        yypop();
    }
    /*
     * "A string may include a 'gap'-—two backslants enclosing white
     * characters—-which is ignored. This allows one to write long strings on
     * more than one line by writing a backslant at the end of one line and at
     * the start of the next." N.b. OpenGrok does not explicltly recognize the
     * "gap" but since a STRING must end in a non-escaped quotation mark, just
     * allow STRINGs to be multi-line regardless of syntax.
     */
}

<CHAR> {    // we don't need to consider the case where prime is part of an identifier since it is handled above
    \\[\'\\]    { out.write(htmlize(yytext())); }
    \'          {
        out.write(htmlize(yytext()));
        yypop();
    }
    /*
     * N.b. though only a single char is valid Haskell syntax, OpenGrok just
     * waits to end CHAR at a non-escaped apostrophe regardless of count.
     */
}

<COMMENT> {
    {WhspChar}*{EOL}    {
        yypop();
        startNewLine();
    }
}

<YYINITIAL, BCOMMENT> {
    "{-"    {
        if (nestedComment++ == 0) {
            pushSpan(BCOMMENT, HtmlConsts.COMMENT_CLASS);
        }
        out.write(yytext());
    }
}

<BCOMMENT> {
    "-}"    {
        out.write(yytext());
        if (--nestedComment == 0) {
            yypop();
        }
    }
}

[&<>\'\"]    { out.write(htmlize(yytext())); }
{WhspChar}*{EOL} { startNewLine();                }
{WhiteSpace}       { out.write(yytext());           }
[!-~]              { out.write(yycharat(0)); }
[^\n]              { writeUnicodeChar(yycharat(0)); }

<STRING, COMMENT, BCOMMENT> {
    {FPath} { out.write(Util.breadcrumbPath(urlPrefix + "path=", yytext(), '/')); }
    {FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+ { writeEMailAddress(yytext()); }
}

<STRING, COMMENT> {
    {BrowseableURI}    {
        appendLink(yytext(), true);
    }
}

<BCOMMENT> {
    /*
     * Right curly bracket is not a valid URI character, so it won't be in a
     * {BrowseableURI} capture, but a hyphen is valid. Thus a nested comment
     * ending token, -}, can hide at the end of a URI. Work around this by
     * capturing a possibly-trailing right curly bracket, and match a special,
     * Haskell-specific collateral capture pattern.
     */
    {BrowseableURI} \}?    {
        appendLink(yytext(), true, HaskellUtils.MAYBE_END_NESTED_COMMENT);
    }
}
