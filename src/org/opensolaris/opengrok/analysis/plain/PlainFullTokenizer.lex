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
 * ident	"@(#)PlainFullTokenizer.lex 1.2     06/02/22 SMI"
 */

package org.opensolaris.opengrok.analysis.plain;
import java.util.*;
import java.io.*;
import org.apache.lucene.analysis.*;
%%

%public
%class PlainFullTokenizer
%extends Tokenizer
%unicode
%function next
%type Token 
%caseless
%switch

%{
  public void close() throws IOException {
  	yyclose();
  }


    /*
     * This would be faster than yytext().tolowercase()
     */
    public String loweryytext() {
        char[] lcs = new char[zzMarkedPos-zzStartRead];
        int k = 0;
        for(int i = zzStartRead; i < zzMarkedPos; i++) {
            char s = zzBuffer[i];
            if(s >= 'A' && s <= 'Z') {
                lcs[k++] =(char)(s + 32);
            } else {
                lcs[k++]= s;
            }
        }
        return new String(lcs);
    }

  public void reInit(char[] buf, int len) {
  	yyreset((Reader) null);
  	zzBuffer = buf;
  	zzEndRead = len;
	zzAtEOF = true;
	zzStartRead = 0;
  }

%}

//WhiteSpace     = [ \t\f\r]+|\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
Printable = [\@\$\%\^\&\-+=\?\.\:]

%%
{Identifier}|{Number}|{Printable}	{return new Token(loweryytext(), zzStartRead, zzMarkedPos);}
<<EOF>>   { return null;} 
.|\n	{}
