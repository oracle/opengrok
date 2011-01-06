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
 * Cross reference a Php file
 */

package org.opensolaris.opengrok.analysis.php;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class PhpXref
%extends JFlexXref
%unicode
%ignorecase
%int
%{
  // TODO move this into an include file when bug #16053 is fixed
  @Override
  protected int getLineNumber() { return yyline; }
  @Override
  protected void setLineNumber(int x) { yyline = x; }
%}

WhiteSpace     = [ \t\f]+
EOL = \r|\n|\r\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]+

URIChar = [\?\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]
FNameChar = [a-zA-Z0-9_\-\.]
File = [a-zA-Z]{FNameChar}* "." ("php"|"php3"|"php4"|"phps"|"phtml"|"inc"|"diff"|"patch")
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[0-9][0-9_]*)([eE][+-]?[0-9]+)?

Pods = "=back" | "=begin" | "=end" | "=for" | "=head1" | "=head2" | "=item" | "=over" | "=pod"
PodEND = "=cut"

%state  STRING SCOMMENT QSTRING POD

%%
<YYINITIAL>{

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline);
}

("$"|"@"|"%"|"&") {Identifier} {
  //we ignore keywords if the identifier starts with one of variable chars ...
    String id = yytext().substring(1);
    out.write(yytext().substring(0,1));
    writeSymbol(id, null, yyline);
}

"<" ({File}|{Path}) ">" {
        out.write("&lt;");
        String path = yytext();
        path = path.substring(1, path.length() - 1);
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");
        out.write("&gt;");
}

{Number}        { out.write("<span class=\"n\">"); out.write(yytext()); out.write("</span>"); }

 \"     { yybegin(STRING);out.write("<span class=\"s\">\"");}
 \'     { yybegin(QSTRING);out.write("<span class=\"s\">\'");}
 "#"   { yybegin(SCOMMENT);out.write("<span class=\"c\">#");}
^ {Pods}   { yybegin(POD);out.write("<span class=\"c\">"+yytext());}
}

<POD> {
^ {PodEND} .* / {EOL} {
    yybegin(YYINITIAL); out.write(yytext()+"</span>");
    // without eol lookahead one could perhaps just use below and use yytext().trim() above ?
    //startNewLine();
  }
}

<STRING> {
  \"     { yybegin(YYINITIAL); out.write("\"</span>"); }
 \\\\   { out.write("\\\\"); }
 \\\"   { out.write("\\\""); }
 {WhiteSpace}*{EOL} {
    yybegin(YYINITIAL); out.write("</span>");
    startNewLine();
  }
}

<QSTRING> {
 "\\\\" { out.write("\\\\"); }
 "\\\'" { out.write("\\\'"); }
 \' {WhiteSpace} \' { out.write(yytext()); }
 \'     { yybegin(YYINITIAL); out.write("'</span>"); }
 {WhiteSpace}*{EOL} {
    yybegin(YYINITIAL); out.write("</span>");
    startNewLine();
  }
}

<SCOMMENT> {
  {WhiteSpace}*{EOL} {
    yybegin(YYINITIAL); out.write("</span>");
    startNewLine();
  }
}


<YYINITIAL, STRING, SCOMMENT, QSTRING, POD> {
"&"     {out.write( "&amp;");}
"<"     {out.write( "&lt;");}
">"     {out.write( "&gt;");}
{WhiteSpace}*{EOL}      { startNewLine(); }
 {WhiteSpace}   { out.write(yytext()); }
 [!-~]  { out.write(yycharat(0)); }
 .      { writeUnicodeChar(yycharat(0)); }
}

<STRING, SCOMMENT, STRING, QSTRING, POD> {
{Path}
        { out.write(Util.breadcrumbPath(urlPrefix+"path=",yytext(),'/'));}

{File}
        {
        String path = yytext();
        out.write("<a href=\""+urlPrefix+"path=");
        out.write(path);
        appendProject();
        out.write("\">");
        out.write(path);
        out.write("</a>");}

("http" | "https" | "ftp" ) "://" ({FNameChar}|{URIChar})+[a-zA-Z0-9/]
        {
         String url = yytext();
         out.write("<a href=\"");
         out.write(url);out.write("\">");
         out.write(url);out.write("</a>");}

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}
