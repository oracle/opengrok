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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Gets Tcl symbols - ignores comments, strings, keywords
 */

package org.opensolaris.opengrok.analysis.tcl;
import java.io.IOException;
import java.io.Reader;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;

%%
%public
%class TclSymbolTokenizer
%extends JFlexTokenizer
%unicode
%type boolean
%eofval{
return false;
%eofval}
%char

%{
    @Override
    public void close() throws IOException {
        yyclose();
    }
%}

Identifier = [\:\=a-zA-Z0-9_]+

%state STRING COMMENT SCOMMENT

%%

<YYINITIAL> {
{Identifier} {String id = yytext();
              if (!Consts.kwd.contains(id)) {
                    setAttribs(id, yychar, yychar + yylength());
                    return true; }
              }
 \"     { yybegin(STRING); }
"#"     { yybegin(SCOMMENT); }
}

<STRING> {
 \"     { yybegin(YYINITIAL); }
\\\\ | \\\"     {}
}

<SCOMMENT> {
\n      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, COMMENT, SCOMMENT> {
<<EOF>>   { return false;}
.|\n    {}
}
