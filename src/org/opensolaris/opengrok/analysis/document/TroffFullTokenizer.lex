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
 * ident	"@(#)TroffFullTokenizer.lex 1.1     05/11/11 SMI"
 */

package org.opensolaris.opengrok.analysis.document;
import java.util.*;
import java.io.*;
import org.apache.lucene.analysis.*;
%%

%public
%class TroffFullTokenizer
%extends Tokenizer
%unicode
%function next
%type Token 
%caseless

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

  public static void main(String argv[]) {
    if (argv.length == 0) {
      System.out.println("Usage : java TroffFullTokenizer <inputfile>");
    }
    else {
      Date start = new Date();
      for (String arg: argv) {
        TroffFullTokenizer scanner = null;
        try {
          scanner = new TroffFullTokenizer( new BufferedReader(new java.io.FileReader(arg)));
	  Token t;
          while ((t = scanner.next()) != null) { 
	  	System.out.println(t.termText() + " ["+t.startOffset()+"-"+ t.endOffset()+"]");
	  }
        }
        catch (Exception e) {
          e.printStackTrace();
        }
	long span =  ((new Date()).getTime() - start.getTime());
	System.err.println("took: "+ span + " msec");
      }
    }
  }

%}

//WhiteSpace     = [ \t\f\r]+|\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
Printable = [\@\$\%\^\&\-+=\?\.\:]

%%
^\.(EQ|in|sp|ne|rt|br|pn|ds|de|if|ig|el|ft|hy|ie|ll|ps|rm|ta|ti|NH|DT|EE)[^\n]* {}
^.[a-zA-Z]{1,2} {}
\\f[ABCIR]  {}
^"...\\\"" {}

\\&.        {return new Token(".", zzStartRead, zzMarkedPos);}
{Identifier}|{Number}|{Printable}	{return new Token(yytext().toLowerCase(), zzStartRead, zzMarkedPos);}
<<EOF>>   { return null;}
.|\n	{}
