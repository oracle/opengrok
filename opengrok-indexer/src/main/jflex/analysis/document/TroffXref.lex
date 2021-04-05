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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2011 Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.document;
import org.opengrok.indexer.analysis.JFlexNonXref;
import java.io.IOException;
import java.io.Writer;
import org.opengrok.indexer.analysis.JFlexXrefUtils;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.indexer.web.Util;
%%
%public
%class TroffXref
%extends JFlexNonXref
%unicode
%int
%char
%init{
    /*
     * Keep this antiquated management of yyline for a JFlexNonXref subclass.
     * Hopefully JFlexNonXref will be retired before too long.
     */
    yyline = 1;
%init}
%include ../CommonLexer.lexh
// do not include CommonXref.lexh in JFlexNonXref subclasses
%{ 
  int p;
  int span;
  int div;

  @Override
  public void write(Writer out) throws IOException {
        p = 0;
        span = 0;
        div = 0;
        this.out = out;

        out.write("</pre><div id=\"man\">");
        while(yylex() != YYEOF) {
        }
        out.write("</div><pre>");
  }

  @Override
  public void startNewLine() throws IOException {
      // *DO NOT CALL super method*

      setLineNumber(++yyline);
      if (didSeePhysicalLOC) {
          ++loc;
          didSeePhysicalLOC = false;
      }
  }

  // Q&D methods to assure well-formed documents
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

  protected void chkLOC() {
      switch (yystate()) {
          case COMMENT:
              break;
          default:
              phLOC();
              break;
      }
  }
%}

%eof{
    if (didSeePhysicalLOC) {
        ++loc;
        didSeePhysicalLOC = false;
    }

    cleanup();
    try {
        while (div > 0) {
            closeDiv();
        }
    } catch (IOException e) {
        // nothing we can do here
    }
%eof}

FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([chtsCHS]|"conf"|"java"|"cpp"|"CC"|"txt"|"htm"|"html"|"pl"|"xml")

%state HEADER COMMENT BOLD TBL TBLL

%include ../Common.lexh
%include ../CommonPath.lexh
%include ../CommonLaxFPath.lexh
%%
<YYINITIAL> {
^\.(SH|TH|SS|IP|NH|TL|UH)       {
    chkLOC(); yybegin(HEADER); cleanup(); openDiv("b");
 }
^(".\\\"")|(\'\\\")|("...\\\"") { yybegin(COMMENT);openSpan('c');}
}

<HEADER> {
{EOL}   { yybegin(YYINITIAL); cleanup(); closeDiv(); startNewLine(); }
}

<COMMENT> {
{EOL}   { yybegin(YYINITIAL); closeSpan(); out.write("<br/>"); startNewLine(); }
}

^\.(B|U|BI|BX|UL|LG|NL|SB|BR|RB) { chkLOC(); yybegin(BOLD); openSpan('b'); }
^\.(I|SM|IB|IR|RI|IX) { chkLOC(); yybegin(BOLD); openSpan('s'); }
^\.(CW) { chkLOC(); yybegin(BOLD); openSpan('k'); }
^\.(DS|LD|ID|BD|CD|RD) { chkLOC(); openSpan('k'); }
^\.DE   { chkLOC(); closeSpan(); }

<BOLD> {
{EOL}      { yybegin(YYINITIAL); closeSpan(); out.write(' '); startNewLine(); }
}

"\\fB"  { chkLOC(); openSpan('b'); }
"\\fI"  { chkLOC(); openSpan('s'); }
"\\fC"|"\\f(CW" { chkLOC(); openSpan('k'); }
"\\fR"  { chkLOC(); closeSpan(); }
"\\fP"  { chkLOC(); closeSpan(); }

^\.(PP|LP|P|TP|IP|HP|PD|SP|br|mk|ce) { 
    chkLOC();
    cleanup();
    openPara();
}

^\.(RS)[^\n]* { chkLOC(); cleanup(); openDiv("rs"); openPara(); }
^\.(RE)[^\n]* { chkLOC(); cleanup(); closeDiv(); }

^\.so { chkLOC(); out.write(".so "); }
^\.(EQ|in|sp|ne|rt|pn|ds|de|if|ig|el|ft|hy|ie|ll|ps|rm|ta|ti|na|ad|te|hw|nh|pl)[^\n]* {EOL} {

    chkLOC();
    startNewLine();
 }
^\.(NH|DT|EE)[^\n]* { chkLOC(); }
^"\\(bu" {EOL}    { chkLOC(); startNewLine(); }
^".nf"  { chkLOC(); closePara(); out.write("<pre>"); }
^".fi"  { chkLOC(); cleanup(); out.write("</pre>"); }
\\\*\(Tm { chkLOC(); out.write("<sup>TM</sup> "); }
\\\*\R { chkLOC(); out.write("&reg; "); }
\\\((l|r)q { chkLOC(); out.write('"'); }
\\\(mi { chkLOC(); out.write('-'); }

^\.TS   {
    chkLOC();
    cleanup();
    yybegin(TBL);
    out.write("<table rules=\"all\"><tr><td>");
 }
<TBL> {
tab\(.\) { chkLOC(); char tab = yycharat(4); }
\.$    { chkLOC(); yybegin(TBLL); }
[[\s]--[\n]]    {}
[^\n]    { chkLOC(); }
}
<TBLL> {
\007    { cleanup(); out.write("</td><td>"); }
^[\_\=] {EOL}    { chkLOC(); startNewLine(); }
T[\{\}] { chkLOC(); }
^\.TE   {
    chkLOC(); yybegin(YYINITIAL); cleanup(); out.write("</td></tr></table>");
 }
{EOL}       { cleanup(); out.write("</td></tr><tr><td>"); startNewLine(); }
}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          chkLOC();
          writeEMailAddress(yytext());
        }

{File} {
        chkLOC();
        String path = yytext();
        out.write("<a href=\"");
        out.write(urlPrefix);
        out.write(QueryParameters.PATH_SEARCH_PARAM_EQ);
        out.write(path);
        JFlexXrefUtils.appendProject(out, project);
        out.write("\">");
        out.write(path);
        out.write("</a>");}

 {RelaxedMiddleFPath}    {
    chkLOC();
    out.write(Util.breadcrumbPath(urlPrefix + QueryParameters.PATH_SEARCH_PARAM_EQ, yytext(), '/'));
 }
\\&.    { chkLOC(); out.write(yycharat(yylength() - 1)); }
\\-     { chkLOC(); out.write('-'); }
"\\ "   { out.write(' '); }
"<"     { chkLOC(); out.write( "&lt;"); }
">"     { chkLOC(); out.write( "&gt;"); }
"&"     { chkLOC(); out.write( "&amp;"); }
{EOL}   { out.write("\n"); startNewLine(); }
{WhspChar}+    { out.write(' '); }
[!-~]   { chkLOC(); out.write(yytext()); }
[[\s]--[\n]]    { Util.htmlize(yytext(), out); }
[^\n]   { chkLOC(); Util.htmlize(yytext(), out); }
