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

package org.opensolaris.opengrok.analysis.document;
import java.util.*;
import java.io.*;
import org.opensolaris.opengrok.web.Util;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.Project;

%%
%public
%class TroffXref
%unicode
%int
%line
%{
  String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();
  boolean p = false;
  Writer out;
  Project project;

  public void setURL(String urlPrefix) {
    this.urlPrefix = urlPrefix;
  }

  public void reInit(char[] buf, int len) {
  	yyreset((Reader) null);
  	zzBuffer = buf;
  	zzEndRead = len;
	zzAtEOF = true;
	zzStartRead = 0;
  }

  public void write(Writer out) throws IOException {
  	this.out = out;
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

WhiteSpace     = [ \t\f\r]

FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([chtsCHS]|"conf"|"java"|"cpp"|"CC"|"txt"|"htm"|"html"|"pl"|"xml")
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*)+[a-zA-Z0-9]

%state HEADER COMMENT BOLD TBL TBLL

%%
<YYINITIAL> {
^\.(SH|TH|SS|IP|NH|TL|UH)	{ yybegin(HEADER);out.write("<div class=\"b\">");}
^(".\\\"")|(\'\\\")|("...\\\"") { yybegin(COMMENT);out.write("<span class=\"c\">");}
}

<HEADER> {
\n	{ yybegin(YYINITIAL);out.write("</div>"); }
}

<COMMENT> {
\n	{ yybegin(YYINITIAL);out.write("</span><br>"); }
}

^\.(B|U|BI|BX|UL|LG|NL|SB|BR|RB) { yybegin(BOLD); out.write("<span class=\"b\">"); }
^\.(I|SM|IB|IR|RI|IX) { yybegin(BOLD); out.write("<span class=\"s\">"); }
^\.(CW) { yybegin(BOLD); out.write("<span class=\"k\">"); }
^\.(DS|LD|ID|BD|CD|RD) { out.write("<span class=\"k\">"); }
^\.DE   { out.write("</span>"); }

<BOLD> {
\n      { yybegin(YYINITIAL);out.write("</span> ");}
}

"\\fB"	{ out.write("<span class=\"b\">"); }
"\\fI"	{ out.write("<span class=\"s\">"); }
"\\fC"|"\\f(CW"	{ out.write("<span class=\"k\">"); }
"\\fR"	{ out.write("</span>"); }
"\\fP"	{ out.write("</span>"); }

^\.(PP|LP|P|TP|IP|HP|PD|SP|br|mk) { 
    if(p) 
        out.write("</p>");
    out.write("<p>");
    p = true;
}

^\.(RS|RE)[^\n]* { out.write("\n"); }

^\.so {out.write(".so ");}
^\.(EQ|in|sp|ne|rt|br|pn|ds|de|if|ig|el|ft|hy|ie|ll|ps|rm|ta|ti)[^\n]*\n {}
^\.(NH|DT|EE)[^\n]* {}
^"\\(bu\n" {}
^".nf"	{out.write("<pre>"); }
^".fi"	{out.write("</pre>"); }
\\\*\(Tm { out.write(" TM "); }
\\\*\R { out.write(" (R) "); }
\\\((l|r)q { out.write('"'); }
\\\(mi { out.write('-'); }

^\.TS   {yybegin(TBL);out.write("<table border=\"1\" cellpadding=\"2\" rules=\"all\" bgcolor=\"#ddddcc\"><tr><td>");}
<TBL> {
tab\(.\) { char tab = yycharat(4); }
\.$    { yybegin(TBLL); }
.    {}
}
<TBLL> {
\007    { out.write("</td><td>"); }
^[\_\=]\n    {}
T[\{\}] {}
^\.TE   { yybegin(YYINITIAL); out.write("</td></tr></table>"); }
\n       { out.write("</td></tr><tr><td>");}
}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
	{
		for(int mi = zzStartRead; mi < zzMarkedPos; mi++) {
			if(zzBuffer[mi] != '@') {
				out.write(zzBuffer[mi]);
			} else {
				out.write(" (at] ");
			}
		}
	}

{File}
	{out.write("<a href=\""+urlPrefix+"path=");
	out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
        appendProject();
        out.write("\">");
	out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
        out.write("</a>");}

{Path}
 	{ out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}
\\&.	{out.write( zzBuffer[zzMarkedPos-1]);}
\\-	{ out.write('-'); }
"\\ "	{ out.write(' '); }
"<"	{out.write( "&lt;");}
">"	{out.write( "&gt;");}
 \n	{ out.write("\n"); }
{WhiteSpace}+	{ out.write(' '); }
[!-~]	{ out.write(yycharat(0)); }
 .	{ }
