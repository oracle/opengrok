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

package org.opensolaris.opengrok.analysis.sh;
import java.util.*;
import java.io.*;
import org.opensolaris.opengrok.web.Util;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.Annotation;

%%
%public
%class ShXref
%unicode
%ignorecase
%int
%line
%{
  String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();
  Writer out;
  Annotation annotation;
  private HashMap<String, HashMap<Integer, String>> defs = null;
  public void setDefs(HashMap<String, HashMap<Integer, String>> defs) {
  	this.defs = defs;
  }

  public void reInit(char[] buf, int len) {
  	yyreset((Reader) null);
  	zzBuffer = buf;
  	zzEndRead = len;
	zzAtEOF = true;
	zzStartRead = 0;
	annotation = null;
  }

  public void write(Writer out) throws IOException {
  	this.out = out;
        Util.readableLine(1, out, annotation);
	yyline = 2;
	while(yylex() != YYEOF) {
	}
  }

  public int getLine() {
  	return yyline-2;
  }

  public static void main(String argv[]) {
    if (argv.length <= 1) {
      System.out.println("Usage : java Xref <inputfile> <outfile>");
    }
    else {
      Date start = new Date();
      ShXref scanner = null;
      try {
          scanner = new ShXref( new BufferedReader(new java.io.FileReader(argv[0])));
	  BufferedWriter out = new BufferedWriter(new java.io.FileWriter(argv[1]));
	  out.write("<html><head><style>a{text-decoration:none;color:#444499;} .I{color:#000099;} .K{color:#000000; font-weight:bold;} .N{color:brown;} .c{color:grey;} .s{color:green;} .l{color: #666699; text-decoration:none; font-weight:normal;background-color:#eee; margin-right:.2em;padding-left:.2em;padding-right:.5em;} .hl{color: black; font-weight:bold; text-decoration:none; background-color:#eee; margin-right:.2em;padding-left:.2em;padding-right:.5em;} .d{color:#909; font-weight:bold; font-style:italic;} .f{color:#909;} .mf{color:#909;}</style></head><body><pre>");
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

WhiteSpace     = [ \t\f\r]
Identifier = [a-zA-Z_] [a-zA-Z0-9_]+
Number = \$? [0-9][0-9]*|[0-9]+.[0-9]+|"0x" [0-9a-fA-F]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([a-zA-Z]+)
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*)+[a-zA-Z0-9]

%state STRING SCOMMENT QSTRING

%%
<STRING>{
 "$" {Identifier} 	{
 			  out.write("<a href=\"");
			  out.write(urlPrefix);
			  out.write("refs=");
			  out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
			  out.write("\">");
			  out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
			  out.write("</a>");
			}
}

<YYINITIAL> {
\$ ? {Identifier}	{ String id = yytext();
 			if(Consts.shkwd.contains(id)) {
				out.write("<b>");out.write(id);out.write("</b>");
			} else {
				HashMap<Integer, String> tags;
				if(defs != null && (tags = defs.get(id)) != null) {
					int sz = 0;
					boolean written = false;
					if (tags.containsKey(new Integer(yyline-1))) {
							out.write("<a class=\"d\" name=\"");
							out.write(id);
							out.write("\"/>");
                                                        out.write("<a href=\"");
				                        out.write(urlPrefix);
                                                        out.write("refs=");
							out.write(id);
							out.write("\" class=\"d\">");
							out.write(id);
							out.write("</a>");
							written = true;
							break;
					} else if (tags.size() == 1) {
						out.write("<a class=\"f\" href=\"#");
						out.write(id);
						out.write("\">");
						out.write(id);
						out.write("</a>");
					} else {
						out.write("<span class=\"mf\">");
						out.write(id);
						out.write("</span>");
					}
				} else {
				out.write("<a href=\""+urlPrefix+"refs=");
				out.write(id);
				out.write("\">");
				out.write(id);
				out.write("</a>");
				}
			}
		}

{Number}	{ out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

 \"	{ yybegin(STRING);out.write("<span class=\"s\">\"");}
 \'	{ yybegin(QSTRING);out.write("<span class=\"s\">\'");}
 "#"	{ yybegin(SCOMMENT);out.write("<span class=\"c\">#");}
}

<STRING> {
 \" {WhiteSpace}* \"  { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);}
 \"	{ yybegin(YYINITIAL); out.write("\"</span>"); }
 \\\\	{ out.write("\\\\"); }
 \\\"	{ out.write("\\\""); }
}

<QSTRING> {
 \' {WhiteSpace}* \' { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); }
 \\'  { out.write("\\'"); }
 \'	{ yybegin(YYINITIAL); out.write("'</span>"); }
}

<SCOMMENT> {
\n { yybegin(YYINITIAL); out.write("</span>");
     Util.readableLine(yyline, out, annotation);}
}


<YYINITIAL, STRING, SCOMMENT, QSTRING> {
{File}
	{out.write("<a href=\""+urlPrefix+"path=");
	out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("\">");
	out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("</a>");}

{Path}
 	{ out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}
"&"	{out.write( "&amp;");}
"<"	{out.write( "&lt;");}
">"	{out.write( "&gt;");}
 \n	{ Util.readableLine(yyline, out, annotation); }
{WhiteSpace}+	{ out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); }
[!-~]	{ out.write(yycharat(0)); }
 .	{ }
}

<STRING, SCOMMENT, QSTRING> {

("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
	{out.write("<a href=\"");
	 out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("\">");
	 out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("</a>");}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
	{
		for(int mi = zzStartRead; mi < zzMarkedPos; mi++) {
			if(zzBuffer[mi] != '@') {
				out.write(zzBuffer[mi]);
			} else {
				out.write(" (at] ");
			}
		}
		//out.write("<a href=\"mailto:");
		//out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("\">");
		//out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("</a>");
	}
}
