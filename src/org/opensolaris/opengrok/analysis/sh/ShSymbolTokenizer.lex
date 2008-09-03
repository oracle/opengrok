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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)ShSymbolTokenizer.lex 1.2     05/12/01 SMI"
 */

package org.opensolaris.opengrok.analysis.sh;
import java.util.*;
import java.io.*;
import org.apache.lucene.analysis.*;
%%
%public
%class ShSymbolTokenizer
%extends Tokenizer
%unicode
%function next
%type Token 

%{
  public void close() throws IOException {
  	yyclose();
  }

  public void reInit(char[] buf, int len) {
  	yyreset((Reader) null);
  	zzBuffer = buf;
  	zzEndRead = len;
	zzAtEOF = true;
	zzStartRead = 0;
  }

%}
Identifier = [a-zA-Z_] [a-zA-Z0-9_]*

%state STRING COMMENT SCOMMENT QSTRING

%%

<YYINITIAL> {
{Identifier} {String id = yytext();
		if(!Consts.shkwd.contains(id))
			return new Token(yytext(), zzStartRead, zzMarkedPos);}
 \"	{ yybegin(STRING); }
 \'	{ yybegin(QSTRING); }
 "#"	{ yybegin(SCOMMENT); }
}

<STRING> {
"$" {Identifier} { return new Token(new String( zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-1 ), zzStartRead, zzMarkedPos);}
"${" {Identifier} "}" { return new Token(new String( zzBuffer, zzStartRead+2, zzMarkedPos-zzStartRead-3 ), zzStartRead, zzMarkedPos);}

 \"	{ yybegin(YYINITIAL); }
\\\\ | \\\"	{}
}

<QSTRING> {
 \'	{ yybegin(YYINITIAL); }
}

<SCOMMENT> {
\n	{ yybegin(YYINITIAL);}
}

<YYINITIAL, STRING, SCOMMENT, QSTRING> {
<<EOF>>   { return null;} 
.|\n	{}
}
