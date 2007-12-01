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
 * ident	"%Z%%M% %I%     %E% SMI"
 */

package org.opensolaris.opengrok.analysis.plain;

import java.util.*;
import java.io.*;
import org.opensolaris.opengrok.web.Util;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;

%%
%public
%class PlainXref
%unicode
%ignorecase
%int
%line
%{
  Writer out;
  String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();
  Annotation annotation;
  public void write(Writer out) throws IOException {
  	this.out = out;
        Util.readableLine(1, out, annotation);
	yyline = 2;
	while(yylex() != YYEOF);
  }
  public void reInit(char[] buf, int len) {
  	yyreset((Reader) null);
  	zzBuffer = buf;
  	zzEndRead = len;
	zzAtEOF = true;
	zzStartRead = 0;
	annotation = null;
  }

  public int getLine() {
  	return yyline - 2;
  }

  public static void main(String argv[]) {
    if (argv.length <= 1) {
      System.out.println("Usage : java Xref <inputfile> <outfile>");
    }
    else {
      Date start = new Date();
      XMLXref scanner = null;
      try {
          scanner = new XMLXref( new BufferedReader(new java.io.FileReader(argv[0])));
	  BufferedWriter out = new BufferedWriter(new java.io.FileWriter(argv[1]));
	  out.write("<html><head><style>.I{color:#000099;} .K{color:#000000; font-weight:bold;} .N{color:brown;} .c{color:grey;} .s{color:green;} .l{color: #666699; text-decoration:none; font-weight:normal;background-color:#eee; margin-right:.2em;padding-left:.2em;padding-right:.5em;}  .hl{color:#666;font-weight:bold; text-decoration:none; background-color:#eee; margin-right:.2em;padding-left:.2em;padding-right:.5em;}</style></head><body><pre>");
	  scanner.write(out);
	  out.write("</pre></body></html>");
	  out.close();
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      long span =  ((new Date()).getTime() - start.getTime());
      System.err.println("took: "+ span + " msec");
     }
  }

%}
URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([a-zA-Z]+) {FNameChar}*
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*)+[a-zA-Z0-9]
%%
{File}|{Path}
	{String s=yytext();
	out.write("<a href=\"");out.write(urlPrefix);out.write("path=");
	out.write(s);out.write("\">");
	out.write(s);out.write("</a>");} 

("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
	{String s=yytext();
	 out.write("<a href=\"");
	 out.write(s);out.write("\">");
	 out.write(s);out.write("</a>");}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
	{	
		for(int mi = zzStartRead; mi < zzMarkedPos; mi++) {
			if(zzBuffer[mi] != '@') {
				out.write(zzBuffer[mi]);
			} else {
				out.write(" (a] ");
			}
		}
/*		String s=yytext();
		out.write("<a href=\"mailto:");
		out.write(s);out.write("\">");
		out.write(s);out.write("</a>");*/
	}

"&"	{out.write( "&amp;");}
"<"	{out.write( "&lt;");}
">"	{out.write( "&gt;");}
\n	{Util.readableLine(yyline, out, annotation); }
[ !-~\t\r\f]	{out.write(yycharat(0));}
.	{}
