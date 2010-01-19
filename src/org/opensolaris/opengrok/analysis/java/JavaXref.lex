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

/*
 * Cross reference a Java file
 */

package org.opensolaris.opengrok.analysis.java;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class JavaXref
%extends JFlexXref
%unicode
%ignorecase
%int
%line
%{
  /* Must match WhiteSpace regex */
  private final static String WHITE_SPACE = "[ \t\f\r]+";

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
%}

/* Must match WHITE_SPACE constant */
WhiteSpace     = [ \t\f]+
EOL = [\r|\n|\r\n|\u2028|\u2029|\u000B|\u000C|\u0085]
Identifier = [a-zA-Z_] [a-zA-Z0-9_]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = [a-zA-Z]{FNameChar}* "." ("java")
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

Number = ([0-9][0-9]*|[0-9]+.[0-9]+|"0x" [0-9a-fA-F]+ )([udl]+)?

JavadocWithClassArg = "@throws" | "@exception"
JavadocWithParamNameArg = "@param"

ClassName = ({Identifier} ".")* {Identifier}
ParamName = {Identifier} | "<" {Identifier} ">"

%state  STRING COMMENT SCOMMENT QSTRING JAVADOC

%%
<YYINITIAL>{
 {Identifier}	{ String id = yytext();
 			if(Consts.kwd.contains(id)) {
				out.write("<b>");out.write(id);out.write("</b>");
			} else {
				if (defs != null && defs.hasSymbol(id)) {
					if (defs.hasDefinitionAt(id, yyline-1)) {
							out.write("<a class=\"d\" name=\"");
							out.write(id);
							out.write("\"/>");
                                                        out.write("<a href=\"");
				                        out.write(urlPrefix);
                                                        out.write("refs=");
							out.write(id);
                                                        appendProject();
							out.write("\" class=\"d\">");
							out.write(id);
							out.write("</a>");
							break;
					} else if (defs.occurrences(id) == 1) {
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
                                    out.write("<a href=\"");
                                    out.write(urlPrefix);
                                    out.write("defs=");
                                    out.write(id);
                                    appendProject();
                                    out.write("\">");
                                    out.write(id);
                                    out.write("</a>");
				}
			}
		}

"<" {File} ">" {out.write("&lt;");
	out.write("<a href=\""+urlPrefix+"path=");
	out.write(zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-2);
        appendProject();
        out.write("\">");
	out.write(zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-2);
        out.write("</a>");
	out.write("&gt;");}

"<" {Path} ">" {out.write("&lt;");
	out.write("<a href=\""+urlPrefix+"path=");
	out.write(zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-2);
        appendProject();
        out.write("\">");
	out.write(zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-2);
        out.write("</a>");
	out.write("&gt;");}

/*{Hier}	
 	{ out.write(Util.breadcrumbPath(urlPrefix+"defs=",yytext(),'.'));}
*/
{Number}	{ out.write("<span class=\"n\">"); out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); out.write("</span>"); }

 \"	{ yybegin(STRING);out.write("<span class=\"s\">\"");}
 \'	{ yybegin(QSTRING);out.write("<span class=\"s\">\'");}
 "/**"  { yybegin(JAVADOC);out.write("<span class=\"c\">/**");}
 "/*"	{ yybegin(COMMENT);out.write("<span class=\"c\">/*");}
 "//"	{ yybegin(SCOMMENT);out.write("<span class=\"c\">//");}
}

<STRING> {
 \" {WhiteSpace} \"  { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);}
 \"	{ yybegin(YYINITIAL); out.write("\"</span>"); }
 \\\\	{ out.write("\\\\"); }
 \\\"	{ out.write("\\\""); }
}

<QSTRING> {
 "\\\\" { out.write("\\\\"); }
 "\\'" { out.write("\\\'"); }
 \' {WhiteSpace} \' { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); }
 \'	{ yybegin(YYINITIAL); out.write("'</span>"); }
}

<COMMENT, JAVADOC> {
"*/"	{ yybegin(YYINITIAL); out.write("*/</span>"); }
}

<JAVADOC> {
  {JavadocWithParamNameArg} {WhiteSpace} {ParamName} |
  {JavadocWithClassArg} {WhiteSpace} {ClassName} {
    String text = yytext();
    String[] tokens = text.split(WHITE_SPACE, 2);
    out.append("<strong>").append(tokens[0]).append("</strong>")
      .append(text.substring(tokens[0].length(),
                             text.length() - tokens[1].length()))
      .append("<em>").append(tokens[1]).append("</em>");
  }
  "@" {Identifier} {
    out.append("<strong>").append(yytext()).append("</strong>");
  }
}

<SCOMMENT> {
  {WhiteSpace}*{EOL} {
    yybegin(YYINITIAL); out.write("</span>");
    Util.readableLine(yyline, out, annotation);
  }
}


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING, JAVADOC> {
"&"	{out.write( "&amp;");}
"<"	{out.write( "&lt;");}
">"	{out.write( "&gt;");}
{WhiteSpace}*{EOL}	{ Util.readableLine(yyline, out, annotation); }
 {WhiteSpace}	{ out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); }
 [!-~]	{ out.write(yycharat(0)); }
 .	{ }
}

<STRING, COMMENT, SCOMMENT, STRING, QSTRING, JAVADOC> {
{Path}
 	{ out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}

{File}
	{
	out.write("<a href=\""+urlPrefix+"path=");
	out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
        appendProject();
        out.write("\">");
	out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
        out.write("</a>");}

("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
	{
	 out.write("<a href=\"");
	 out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("\">");
	 out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("</a>");}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
	{
		for(int mi = zzStartRead; mi < zzMarkedPos; mi++) {
			if(zzBuffer[mi] != '@') {
				out.write(zzBuffer[mi]);
			} else {
				out.write(" (at) ");
			}
		}
		//out.write("<a href=\"mailto:");
		//out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("\">");
		//out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("</a>");
	}
}
