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
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.search.context;

import org.opensolaris.opengrok.search.Hit;
import java.io.*;
import java.util.*;
%%

%public
%class HistoryLineTokenizer
%unicode
%function next
%type String 
%ignorecase
%char

%{
  public static final HashSet<String> stopset = new HashSet<String>();
  static {
  stopset.add(  "a");
stopset.add( "an");
stopset.add( "and");
stopset.add( "are");
stopset.add( "as");
stopset.add( "at");
stopset.add( "be");
stopset.add( "but");
stopset.add( "by");
stopset.add( "for");
stopset.add( "if");
stopset.add( "in");
stopset.add( "into");
stopset.add( "is");
stopset.add( "it");
stopset.add( "no");
stopset.add( "not");
stopset.add( "of");
stopset.add( "on");
stopset.add( "or");
stopset.add( "s");
stopset.add( "such");
stopset.add( "t");
stopset.add( "that");
stopset.add( "the");
stopset.add( "their");
stopset.add( "then");
stopset.add( "there");
stopset.add( "these");
stopset.add( "they");
stopset.add( "this");
stopset.add( "to");
stopset.add( "was");
stopset.add( "will");
stopset.add( "with");
stopset.add( "/");
stopset.add( "\\");
stopset.add(":");
stopset.add(".");
stopset.add("0.0");
stopset.add( "1.0");
  }

  public void reInit(String str) {
      yyreset(new StringReader(str));
  }

  /** Return the position of the first character in the current token. */
  int getMatchStart() {
      return yychar;
  }

  /** Return the position of the first character after the current token. */
  int getMatchEnd() {
      return yychar + yylength();
  }
%}

Identifier = [a-zA-Z_] [a-zA-Z0-9_]*
Number = [0-9]+|[0-9]+\.[0-9]+| "0[xX]" [0-9a-fA-F]+
Printable = [\@\$\%\^\&\-+=\?\.\:]

%%


{Identifier}|{Number}|{Printable}       {String m = yytext();
                                        if(stopset.contains(m)) { } else { return(m);}}
<<EOF>>   { return null;}
.|\n    {}
