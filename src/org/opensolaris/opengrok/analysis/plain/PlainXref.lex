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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis.plain;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class PlainXref
%extends JFlexXref
%unicode
%ignorecase
%int
%line
%{
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

%}
URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([a-zA-Z]+) {FNameChar}*
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*)+[a-zA-Z0-9]
%%
{File}|{Path}
	{String s=yytext();
	out.write("<a href=\"");out.write(urlPrefix);out.write("path=");
	out.write(s);appendProject();out.write("\">");
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

// Bug #13362: If there's a very long sequence that matches {FNameChar}+,
// parsing the file will take forever because of all the backtracking. With
// this rule, we avoid much of the backtracking and speed up the parsing
// (in some cases from hours to seconds!). This rule will not interfere with
// the rules above because JFlex always picks the longest match.
{FNameChar}+ { out.write(yytext()); }

"&"	{out.write( "&amp;");}
"<"	{out.write( "&lt;");}
">"	{out.write( "&gt;");}
\n	{Util.readableLine(yyline, out, annotation); }
[ !-~\t\r\f]	{out.write(yycharat(0));}
.	{}
