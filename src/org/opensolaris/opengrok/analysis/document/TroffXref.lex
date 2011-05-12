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
 *
 * Portions Copyright 2011 Jens Elkner.
 */

package org.opensolaris.opengrok.analysis.document;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class TroffXref
%extends JFlexXref
%unicode
%int
%{ 
  int p;
  int span;
  int div;

  @Override
  public void write(Writer out) throws IOException {
  		p = 0;
  		span = 0;
  		div = 0;
        yyline++;
        this.out = out;
        while(yylex() != YYEOF) {
        }
  }

  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
  
  // Q&D methods to asure well-formed documents
  protected void closePara() throws IOException {
  	if (p > 0) {
  		out.write("</p>");
  		p--;
  	}
  }
  protected void closeSpan() throws IOException {
  	if (span > 0) {
  		out.write("</span>");
  		span--;
  	}
  }
  protected void closeDiv() throws IOException {
  	if (div > 0) {
  		out.write("</div>");
  		div--;
  	}
  }
  protected void openPara() throws IOException {
  	out.write("<p>");
  	p++;
  }
  protected void openSpan(char cssClass) throws IOException {
  	out.write("<span class=\"");
  	out.write(cssClass);
  	out.write("\">");
  	span++;
  }

  protected void openDiv(String cssClass) throws IOException {
  	out.write("<div class=\"");
  	out.write(cssClass);
  	out.write("\">");
  	div++;
  }
  
  protected void cleanup() {
    try {
	  while (span > 0) {
	  	closeSpan();
	  }
	  while (p > 0) {
	  	closePara();
	  }
	} catch (IOException e) {
		// nothing we can do here
	}
  }
%}

%eof{
	cleanup();
	try {
		while (div > 0) {
			closeDiv();
		}
	} catch (IOException e) {
		// nothing we can do here
	}
%eof}

WhiteSpace     = [ \t\f]
EOL = \r|\n|\r\n
FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([chtsCHS]|"conf"|"java"|"cpp"|"CC"|"txt"|"htm"|"html"|"pl"|"xml")
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*)+[a-zA-Z0-9]

%state HEADER COMMENT BOLD TBL TBLL

%%
<YYINITIAL> {
^\.(SH|TH|SS|IP|NH|TL|UH)       { yybegin(HEADER); cleanup(); openDiv("b");}
^(".\\\"")|(\'\\\")|("...\\\"") { yybegin(COMMENT);openSpan('c');}
}

<HEADER> {
{EOL}   { yybegin(YYINITIAL); cleanup(); closeDiv(); yyline++;}
}

<COMMENT> {
{EOL}   { yybegin(YYINITIAL); closeSpan(); out.write("<br/>"); yyline++;}
}

^\.(B|U|BI|BX|UL|LG|NL|SB|BR|RB) { yybegin(BOLD); openSpan('b'); }
^\.(I|SM|IB|IR|RI|IX) { yybegin(BOLD); openSpan('s'); }
^\.(CW) { yybegin(BOLD); openSpan('k'); }
^\.(DS|LD|ID|BD|CD|RD) { openSpan('k'); }
^\.DE   { closeSpan(); }

<BOLD> {
{EOL}      { yybegin(YYINITIAL); closeSpan(); out.write(' '); yyline++;}
}

"\\fB"  { openSpan('b'); }
"\\fI"  { openSpan('s'); }
"\\fC"|"\\f(CW" { openSpan('k'); }
"\\fR"  { closeSpan(); }
"\\fP"  { closeSpan(); }

^\.(PP|LP|P|TP|IP|HP|PD|SP|br|mk|ce) { 
    cleanup();
    openPara();
}

^\.(RS)[^\n]* { cleanup(); openDiv("rs"); openPara(); }
^\.(RE)[^\n]* { cleanup(); closeDiv(); }

^\.so {out.write(".so ");}
^\.(EQ|in|sp|ne|rt|pn|ds|de|if|ig|el|ft|hy|ie|ll|ps|rm|ta|ti|na|ad|te|hw|nh|pl)[^\n]*\n { }
^\.(NH|DT|EE)[^\n]* {}
^"\\(bu\n" {}
^".nf"  {closePara(); out.write("<pre>"); }
^".fi"  {cleanup(); out.write("</pre>"); }
\\\*\(Tm { out.write("<sup>TM</sup> "); }
\\\*\R { out.write("&reg; "); }
\\\((l|r)q { out.write('"'); }
\\\(mi { out.write('-'); }

^\.TS   { cleanup(); yybegin(TBL);out.write("<table rules=\"all\"><tr><td>");}
<TBL> {
tab\(.\) { char tab = yycharat(4); }
\.$    { yybegin(TBLL); }
.    {}
}
<TBLL> {
\007    { cleanup(); out.write("</td><td>"); }
^[\_\=]\n    {}
T[\{\}] {}
^\.TE   { yybegin(YYINITIAL); cleanup(); out.write("</td></tr></table>"); }
{EOL}       { cleanup(); out.write("</td></tr><tr><td>"); yyline++;}
}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }

{File} {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");}

{Path}
        { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}
\\&.    {out.write(yycharat(yylength() - 1));}
\\-     { out.write('-'); }
"\\ "   { out.write(' '); }
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
"&"		{out.write( "&amp;");}
{EOL}   { out.write("\n"); yyline++;}
{WhiteSpace}+   { out.write(' '); }
[!-~]   { out.write(yycharat(0)); }
 .      { writeUnicodeChar(yycharat(0)); }
