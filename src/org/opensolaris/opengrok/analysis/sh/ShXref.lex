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

package org.opensolaris.opengrok.analysis.sh;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;
import java.util.Stack;

%%
%public
%class ShXref
%extends JFlexXref
%unicode
%ignorecase
%int
%line
%{
  private final Stack<Integer> stateStack = new Stack<Integer>();
  private final Stack<String> styleStack = new Stack<String>();

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

  private void pushstate(int state, String style) throws IOException {
    if (!styleStack.empty()) {
      out.write("</span>");
    }
    if (style == null) {
      out.write("<span>");
    } else {
      out.write("<span class=\"" + style + "\">");
    }
    stateStack.push(yystate());
    styleStack.push(style);
    yybegin(state);
  }

  private void popstate() throws IOException {
    out.write("</span>");
    yybegin(stateStack.pop());
    styleStack.pop();
    if (!styleStack.empty()) {
      String style = styleStack.peek();
      if (style == null) {
        out.write("<span>");
      } else {
        out.write("<span class=\"" + style + "\">");
      }
    }
  }

%}

WhiteSpace     = [ \t\f]
EOL = \r|\n|\r\n|\u2028|\u2029|\u000B|\u000C|\u0085
Identifier = [a-zA-Z_] [a-zA-Z0-9_]+
Number = \$? [0-9][0-9]*|[0-9]+.[0-9]+|"0x" [0-9a-fA-F]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = {FNameChar}+ "." ([a-zA-Z]+)
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*)+[a-zA-Z0-9]

%state STRING SCOMMENT QSTRING SUBSHELL BACKQUOTE

%%
<STRING>{
 "$" {Identifier}       {
                          out.write("<a href=\"");
                          out.write(urlPrefix);
                          out.write("refs=");
                          out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
                          appendProject();
                          out.write("\">");
                          out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
                          out.write("</a>");
                        }

  /* This rule matches associative arrays inside strings,
     for instance "${array["string"]}". Push a new STRING
     state on the stack to prevent premature exit from the
     STRING state. */
  \$\{ {Identifier} \[\" {
    out.write(yytext()); pushstate(STRING, "s");
  }
}

<YYINITIAL, SUBSHELL, BACKQUOTE> {
\$ ? {Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.shkwd, yyline - 1);
}

{Number}        { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

 \$ ? \" { pushstate(STRING, "s"); out.write(yytext()); }
 \$ ? \' { pushstate(QSTRING, "s"); out.write(yytext()); }
 "#"     { pushstate(SCOMMENT, "c"); out.write(yytext()); }
}

<STRING> {
 \" {WhiteSpace}* \"  { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);}
 \"     { out.write(yytext()); popstate(); }
 \\\\ | \\\" | \\\$   { out.write(yytext()); }
 \$\(   { pushstate(SUBSHELL, null); out.write(yytext()); }
 `      { pushstate(BACKQUOTE, null); out.write(yytext()); }
}

<QSTRING> {
 \' {WhiteSpace}* \' { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); }
 \\'  { out.write("\\'"); }
 \'   { out.write(yytext()); popstate(); }
}

<SCOMMENT> {
{EOL} { popstate();
     Util.readableLine(yyline, out, annotation);}
}

<SUBSHELL> {
  \)   { out.write(yytext()); popstate(); }
}

<BACKQUOTE> {
  ` { out.write(yytext()); popstate(); }
}

<YYINITIAL, SUBSHELL, BACKQUOTE> {
  \\` | \\\( | \\\) | \\\\ { out.write(yytext()); }
  \$ ? \( { pushstate(SUBSHELL, null); out.write(yytext()); }
  ` { pushstate(BACKQUOTE, null); out.write(yytext()); }
}

<YYINITIAL, SUBSHELL, BACKQUOTE, STRING, SCOMMENT, QSTRING> {
{File}
        {out.write("<a href=\""+urlPrefix+"path=");
        out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
        appendProject();
        out.write("\">");
        out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
        out.write("</a>");}

{Path}
        { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}
"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
 {EOL}  { Util.readableLine(yyline, out, annotation); }
{WhiteSpace}+   { out.write(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead); }
[!-~]   { out.write(yycharat(0)); }
 .      { writeUnicodeChar(yycharat(0)); }
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
