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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * Gets Tcl symbols - ignores comments, strings, keywords
 */

package org.opensolaris.opengrok.analysis.tcl;
import java.util.*;
import java.io.*;
import org.apache.lucene.analysis.*;

%%
%public
%class TclSymbolTokenizer
%extends Tokenizer
%unicode
%function next
%type Token

%{

  public void close() {
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
      System.out.println("Usage : java TclSymbolTokenizer <inputfiles>");
    }
    else {
      Date start = new Date();
      for (String arg: argv) {
        TclSymbolTokenizer scanner = null;
        try {
          scanner = new TclSymbolTokenizer(new BufferedReader(new FileReader(arg)));
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
Identifier = [\:\=a-zA-Z0-9_]+

%state STRING COMMENT SCOMMENT

%%

<YYINITIAL> {
{Identifier} {String id = yytext();
              if (!Consts.kwd.contains(id))
                return new Token(yytext(), zzStartRead, zzMarkedPos);}
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
<<EOF>>   { return null;}
.|\n    {}
}
