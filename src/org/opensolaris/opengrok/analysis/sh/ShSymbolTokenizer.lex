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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis.sh;
import java.io.IOException;
import java.io.Reader;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
%%
%public
%class ShSymbolTokenizer
%extends JFlexTokenizer
%unicode
%type boolean
%eofval{
return false;
%eofval}
%char

%{
    public void reInit(char[] buf, int len) {
        yyreset((Reader) null);
        zzBuffer = buf;
        zzEndRead = len;
        zzAtEOF = true;
        zzStartRead = 0;
    }

    @Override
    public void close() throws IOException {
        yyclose();
    }
%}

Identifier = [a-zA-Z_] [a-zA-Z0-9_]*

%state STRING COMMENT SCOMMENT QSTRING

%%

<YYINITIAL> {
{Identifier} {String id = yytext();
                if(!Consts.shkwd.contains(id)){
                        setAttribs(id, yychar, yychar + yylength());
                        return true; }
              }
 \"     { yybegin(STRING); }
 \'     { yybegin(QSTRING); }
 "#"    { yybegin(SCOMMENT); }
}

<STRING> {
"$" {Identifier} {
    setAttribs(yytext().substring(1), yychar + 1, yychar + yylength());
    return true;
}

"${" {Identifier} "}" {
    setAttribs(yytext().substring(2), yychar + 2, yychar + yylength() - 1);
    return true;
}

 \"     { yybegin(YYINITIAL); }
\\\\ | \\\"     {}
}

<QSTRING> {
 \'     { yybegin(YYINITIAL); }
}

<SCOMMENT> {
\n      { yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, SCOMMENT, QSTRING> {
<<EOF>>   { return false;}
.|\n    {}
}
