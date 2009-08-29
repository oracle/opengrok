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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * Cross reference a Fortran file
 */
package org.opensolaris.opengrok.analysis.fortran;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class FortranXref
%unicode
%ignorecase
%int
%line
%{
  Writer out;
  String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();
  Annotation annotation;
  Project project;
  private Definitions defs;
  public void setDefs(Definitions defs) {
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

  private void appendProject() throws IOException {
      if (project != null) {
          out.write("&project=");
          out.write(project.getPath());
      }
  }
%}

WhiteSpace     = [ \t\f\r]+
Identifier = [a-zA-Z_] [a-zA-Z0-9_]+
Label = [0-9]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = [a-zA-Z]{FNameChar}* ".inc"
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

Number = ([0-9][0-9]*|[0-9]+.[0-9]+|"0x" [0-9a-fA-F]+ )([udl]+)?

%state  STRING COMMENT SCOMMENT QSTRING LCOMMENT

%%
<YYINITIAL>{
 ^{Label} { out.write("<span class=\"n\">"); out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); out.write("</span>"); }
 ^[^ \t\f\r\n]+	{ String commentStr = yytext(); yybegin(LCOMMENT);out.write("<span class=\"c\">"+commentStr);}
 {Identifier}	{ String id = yytext();
                  if (Consts.kwd.contains(id.toLowerCase())) {
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
	out.write(zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-2);out.write("\">");
	out.write(zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-2);out.write("</a>");
	out.write("&gt;");}

"<" {Path} ">" {out.write("&lt;");
	out.write("<a href=\""+urlPrefix+"path=");
	out.write(zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-2);out.write("\">");
	out.write(zzBuffer, zzStartRead+1, zzMarkedPos-zzStartRead-2);out.write("</a>");
	out.write("&gt;");}

/*{Hier}	
 	{ out.write(Util.breadcrumbPath(urlPrefix+"defs=",yytext(),'.'));}
*/
{Number}	{ out.write("<span class=\"n\">"); out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); out.write("</span>"); }

 \"	{ yybegin(STRING);out.write("<span class=\"s\">\"");}
 \'	{ yybegin(QSTRING);out.write("<span class=\"s\">\'");}
 \!	{ yybegin(SCOMMENT);out.write("<span class=\"c\">!");}
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

<COMMENT> {
"*/"	{ yybegin(YYINITIAL); out.write("*/</span>"); }
}

<SCOMMENT> {
{WhiteSpace}*\n	{ yybegin(YYINITIAL); out.write("</span>");
                  Util.readableLine(yyline, out, annotation);}
}

<LCOMMENT> {
"&"	{out.write( "&amp;");}
"<"	{out.write( "&lt;");}
">"	{out.write( "&gt;");}
{WhiteSpace}*\n	{ yybegin(YYINITIAL); out.write("</span>");
                  Util.readableLine(yyline, out, annotation);}
 {WhiteSpace}	{ out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); }
 [!-~]	{ out.write(yycharat(0)); }
 .	{ }
}


<YYINITIAL, STRING, COMMENT, SCOMMENT, QSTRING> {
"&"	{out.write( "&amp;");}
"<"	{out.write( "&lt;");}
">"	{out.write( "&gt;");}
{WhiteSpace}*\n	{ Util.readableLine(yyline, out, annotation); }
 {WhiteSpace}	{ out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); }
 [!-~]	{ out.write(yycharat(0)); }
 .	{ }
}

<STRING, COMMENT, SCOMMENT, STRING, QSTRING> {
{Path}
 	{ out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}

{File}
	{
	out.write("<a href=\""+urlPrefix+"path=");
	out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("\">");
	out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);out.write("</a>");}

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
