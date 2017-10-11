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
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

/*
 * Cross reference a Perl file
 */

package org.opensolaris.opengrok.analysis.perl;
import org.opensolaris.opengrok.analysis.JFlexXref;
import java.io.IOException;
import java.io.Writer;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.web.Util;

%%
%public
%class PerlXref
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

  // Gets a value indicating if the quote should be ended. Also, recognize
  // quote-like operators which allow nesting to increase the nesting level
  // if appropriate.
  boolean isQuoteEnding(String match)
  {
    char c = match.charAt(0);
    if (c == endqchar) {
        if (--nqchar <= 0) {
            return true;
        } else if (nestqchar != '\0') {
            waitq = true;
        }
    } else if (nestqchar != '\0' && c == nestqchar) {
        if (waitq) {
            waitq = false;
        } else {
            ++nqchar;
        }
    }
    return false;
  }

  // Starts a quote-like operator as specified in a syntax fragment, `op',
  // and write the operator to output.
  void qop(String op, int namelength, boolean nointerp) throws IOException
  {
    qop(true, op, namelength, nointerp);
  }

  // Starts a quote-like operator as specified in a syntax fragment, `op',
  // and write the operator to output if `doWrite` is true.
  void qop(boolean doWrite, String op, int namelength,
    boolean nointerp) throws IOException
  {
    // If namelength is positive, allow that a non-zero-width word boundary
    // character may have needed to be matched since jflex does not conform
    // with \b as a zero-width simple word boundary. Excise it into `boundary'.
    String boundary = "";
    if (namelength > 0) {
        boundary = op;
        op = op.replaceAll("^\\W+", "");
        boundary = boundary.substring(0, boundary.length() - op.length());
    }
    String opname = op.substring(0, namelength);
    waitq = false;

    switch (opname) {
        case "tr":
        case "s":
        case "y":
            nqchar = 2;
            break;
        default:
            nqchar = 1;
            break;
    }

    String postop = op.substring(opname.length());
    String ltpostop = postop.replaceAll("^\\s+", "");
    char opc = ltpostop.charAt(0);
    setEndQuoteChar(opc);
    setState(ltpostop, nointerp);

    if (doWrite) {
        out.write(htmlize(boundary));
        writeSymbol(opname, Consts.kwd, yyline);
        out.write(Ss);
        out.write(htmlize(postop));
    }
  }

  // Sets the jflex state reflecting `ltpostop' and `nointerp'.
  void setState(String ltpostop, boolean nointerp)
  {
    int state;
    boolean nolink = false;

    // "no link" for {FNameChar} or {URIChar}, which covers everything in the
    // rule for "string links" below
    if (ltpostop.matches("^[a-zA-Z0-9_]")) {
        nolink = true;

        // it's impossible to have a dynamic pattern in jflex, which would be
        // required to continue to "interpolate OpenGrok links" but exclude the
        // initial character from `ltpostop' -- so just disable it entirely.
        nointerp = true; // override
    } else if (ltpostop.matches("^[\\?\\#\\+%&:/\\.@_;=\\$,\\-!~\\*\\\\]")) {
        nolink = true;
    }

    if (nointerp) {
        state = nolink ? QUOxLxN : QUOxN;
    } else {
        state = nolink ? QUOxL : QUO;
    }
    saveAndBegin(state);
  }

  // Sets a special `endqchar' if appropriate for `opener' or just tracks
  // `opener'
  void setEndQuoteChar(char opener)
  {
    switch (opener) {
        case '[':
            nestqchar = opener;
            endqchar = ']';
            break;
        case '<':
            nestqchar = opener;
            endqchar = '>';
            break;
        case '(':
            nestqchar = opener;
            endqchar = ')';
            break;
        case '{':
            nestqchar = opener;
            endqchar = '}';
            break;
        default:
            nestqchar = '\0';
            endqchar = opener;
            break;
    }
  }

  // Begins a quote-like state for a heuristic match of the shorthand // of m//
  // where the `capture' ends with "/", begins with punctuation, and the
  // intervening whitespace may contain LFs -- and writes the parts to output.
  void hqopPunc(String capture) throws IOException
  {
    // `preceding' is everything before the '/'; 'lede' is the initial part
    // before any whitespace; and `intervening' is any whitespace.
    String preceding = capture.substring(0, capture.length() - 1);
    String lede = preceding.replaceAll("\\s+$", "");
    String intervening = preceding.substring(lede.length());

    qop(false, "/", 0, false);
    out.write(htmlize(lede));
    writeWhitespace(intervening);
    out.write(Ss);
    out.write("/");
  }

  // Begins a quote-like state for a heuristic match of the shorthand // of m//
  // where the `capture' ends with "/", begins with an initial symbol, and the
  // intervening whitespace may contain LFs -- and writes the parts to output.
  void hqopSymbol(String capture) throws IOException
  {
    // `preceding' is everything before the '/'; 'lede' is the initial part
    // before any whitespace; and `intervening' is any whitespace.
    String preceding = capture.substring(0, capture.length() - 1);
    String lede = preceding.replaceAll("\\s+$", "");
    String intervening = preceding.substring(lede.length());

    qop(false, "/", 0, false);
    writeSymbol(lede, Consts.kwd, yyline);
    writeWhitespace(intervening);
    out.write(Ss);
    out.write("/");
  }

  // Write `whsp' to output -- if it does not contain any LFs then the full
  // String is written; otherwise, pre-LF spaces are condensed as usual.
  void writeWhitespace(String whsp) throws IOException {
    int i;
    if ((i = whsp.indexOf("\n")) == -1) {
        out.write(whsp);
    } else {
        int numlf = 1, off = i + 1;
        while ((i = whsp.indexOf("\n", off)) != -1) {
            ++numlf;
            off = i + 1;
        }
        while (numlf-- > 0) startNewLine();
        if (off < whsp.length()) out.write(whsp.substring(off));
    }
  }

  // Saves the yystate(), and begins `state'.
  void saveAndBegin(int state)
  {
    prestate = yystate();
    yybegin(state);
  }

  // Restores the state from saveAndBegin().
  void restoreState()
  {
    yybegin(prestate);
    prestate = 0;
  }

  // If the state is YYINITIAL, then transitions to INTRA; otherwise does
  // nothing, because other transitions would have saved the state.
  void maybeIntraState()
  {
    if (yystate() == YYINITIAL) yybegin(INTRA);
  }

  // Begins a Here-document state, and writes the `capture' to output.
  void hop(String capture, boolean nointerp,
    boolean indented) throws IOException
  {
    int state;

    hereTerminator = null;
    Matcher m = hereTerminatorMatch.matcher(capture);
    if (m.find()) hereTerminator = m.group(0);

    if (nointerp) {
        state = indented ? HEREinxN : HERExN;
    } else {
        state = indented ? HEREin : HERE;
    }
    saveAndBegin(state);

    out.write(htmlize(capture));
    out.write(Ss);
  }

  // Writes the `capture' to output, possibly ending the Here-document state
  // just beforehand.
  void maybeEndHere(String capture) throws IOException
  {
    if (!isHereEnding(capture)) {
        out.write(htmlize(capture));
    } else {
        restoreState();
        out.write(_S);
        out.write(htmlize(capture));
    }
  }

  // Gets a value indicating if the Here-document should be ended.
  boolean isHereEnding(String capture)
  {
    String trimmed = capture.replaceAll("^\\s+", "");
    return trimmed.equals(hereTerminator);
  }

  final static String Sc = "<span class=\"c\">";
  final static String Sn = "<span class=\"n\">";
  final static String Ss = "<span class=\"s\">";
  final static String _S = "</span>";

  final static Pattern hereTerminatorMatch = Pattern.compile("[a-zA-Z0-9_]+$");

  // When matching a quoting construct like qq[], q(), m//, s```, etc., the
  // terminating character is stored
  char endqchar;

  // When matching a quoting construct like qq[], q(), m<>, s{}{} that nest,
  // the begin character ('[', '<', '(', or '{') is stored so that nesting
  // is tracked and nqchar is incremented appropriately. Otherwise, `nestqchar'
  // is set to '\0' if no nesting occurs.
  char nestqchar;

  // When matching a quoting construct like qq//, m//, or s```, etc., the
  // number of remaining end separators in the operator is stored. E.g., for
  // m//, it is 1; for tr/// it is 2. Nesting increases the value when seen.
  int nqchar;

  // When matching a two part construct like tr/// or s```, etc., after the
  // first end separator, `waitingqchar' is set to TRUE so that nesting is not
  // active.
  boolean waitq;

  // When matching a quote-like operator, the previous yystate() is stored to
  // be restored at the end of the quote
  int prestate;

  // Stores the terminating identifier for For Here-documents
  String hereTerminator;
%}

WhiteSpace     = [ \t\f]+
MaybeWhsp     = [ \t\f]*
EOL = \r|\n|\r\n
Identifier = [a-zA-Z_] [a-zA-Z0-9_]+
Sigils = ("$" | "@" | "%" | "&" | "*")
WxSigils = [[\W]--[\$\@\%\&\*]]

// see also: setState() which mirrors this regex
URIChar = [\?\#\+\%\&\:\/\.\@\_\;\=\$\,\-\!\~\*\\]

FNameChar = [a-zA-Z0-9_\-\.]
FileExt = ("pl"|"perl"|"pm"|"conf"|"txt"|"htm"|"html"|"xml"|"ini"|"diff"|"patch")
File = [a-zA-Z]{FNameChar}* "." {FileExt}
Path = "/"? [a-zA-Z]{FNameChar}* ("/" [a-zA-Z]{FNameChar}*[a-zA-Z0-9])+

Number = (0[xX][0-9a-fA-F]+|[0-9]+\.[0-9]+|[0-9][0-9_]*)([eE][+-]?[0-9]+)?

Pods = "=back" | "=begin" | "=end" | "=for" | "=head1" | "=head2" | "=item" | "=over" | "=pod"
PodEND = "=cut"

Quo0 =           [[\`\(\)\<\>\[\]\{\}\p{P}]]
Quo0xHash =      [[\`\(\)\<\>\[\]\{\}\p{P}]--\#]
Quo0xHashxApos = [[\`\(\)\<\>\[\]\{\}\p{P}]--[\#\']]

MSapos = [ms] {MaybeWhsp} \'
MShash = [ms]\#
MSpunc = [ms] {MaybeWhsp} {Quo0xHashxApos}
MSword = [ms] {WhiteSpace} \w
QYhash = [qy]\#
QYpunc = [qy] {MaybeWhsp} {Quo0xHash}
QYword = [qy] {WhiteSpace} \w

QXRapos  = "q"[xr] {MaybeWhsp} \'
QQXRhash = "q"[qxr]\#
QQXRPunc = "q"[qxr] {MaybeWhsp} {Quo0xHash}
QQXRword = "q"[qxr] {WhiteSpace} \w

QWhash = "qw"\#
QWpunc = "qw" {MaybeWhsp} {Quo0xHash}
QWword = "qw" {WhiteSpace} \w
TRhash = "tr"\#
TRpunc = "tr" {MaybeWhsp} {Quo0xHash}
TRword = "tr" {WhiteSpace} \w

HereContinuation = \,{MaybeWhsp} "<<"\~? {MaybeWhsp}
MaybeHereMarkers = ([\"\'\`\\]?{Identifier} [^\n]* {HereContinuation})?

//
// Track some keywords that can be used to identify heuristically a possible
// beginning of the shortcut syntax, //, for m//. Also include any perlfunc
// that takes /PATTERN/ -- which is just "split". Heuristics using punctuation
// are defined inline later in some rules.
//
Mwords_1 = ("eq" | "ne" | "le" | "ge" | "lt" | "gt" | "cmp")
Mwords_2 = ("if" | "unless" | "or" | "and" | "not")
Mwords_3 = ("split")
Mwords = ({Mwords_1} | {Mwords_2} | {Mwords_3})

Mpunc1YYIN = [\(\!]
Mpunc2IN = ([!=]"~" | [\:\?\=\+\-\<\>] | "=="|"!="|"<="|">="|"<=>"|"&&" | "||")

//
// There are two dimensions to quoting: "link"-or-not and "interpolate"-or-not.
// Unfortunately, we cannot control the %state values, so we have to declare
// a cross-product of states. (Technically, state values are not guaranteed to
// be unique by jflex, but states that do not have identical rules will have
// different values. The following four "QUO" states satisfy this difference
// criterion. Likewise with the four "HERE" states.)
//
// YYINITIAL : nothing yet parsed or just after a non-quoted [;{}]
// INTRA : saw content from YYINITIAL but not yet other state or [;{}]
// SCOMMENT : single-line comment
// POD : Perl Plain-Old-Documentation
// QUO : quote-like that is OK to match paths|files|URLs|e-mails
// QUOxN : "" but with no interpolation
// QUOxL : quote-like that is not OK to match paths|files|URLs|e-mails
//      because a non-traditional character is used as the quote-like delimiter
// QUOxLxN : "" but with no interpolation
// HERE : Here-docs
// HERExN : Here-docs with no interpolation
// HEREin : Indented Here-docs
// HEREinxN : Indented Here-docs with no interpolation
//
%state  INTRA SCOMMENT POD QUO QUOxN QUOxL QUOxLxN HERE HERExN HEREin HEREinxN

%%
<HERE, HERExN> {
    ^ {Identifier} / {MaybeWhsp}{EOL}  { maybeEndHere(yytext()); }
}

<HEREin, HEREinxN> {
    ^ {MaybeWhsp} {Identifier} / {MaybeWhsp}{EOL}   { maybeEndHere(yytext()); }
}

<YYINITIAL, INTRA>{

    [;\{\}]    {
        yybegin(YYINITIAL);
        prestate = YYINITIAL;
        out.write(yytext());
    }

 // Following are rules for Here-documents. Stacked multiple here-docs are
 // recognized, but not fully supported, as only the interpolation setting
 // of the first marker will apply to all sections. (The final, second HERE
 // quoting character is not demanded, as it is superfluous for the needs of
 // xref lexing; and leaving it off simplifies parsing.)

 "<<"  {MaybeWhsp} {MaybeHereMarkers} [\"\`]?{Identifier}    {
    hop(yytext(), false/*nointerp*/, false/*indented*/);
 }
 "<<~" {MaybeWhsp} {MaybeHereMarkers} [\"\`]?{Identifier}    {
    hop(yytext(), false/*nointerp*/, true/*indented*/);
 }
 "<<"  {MaybeWhsp} {MaybeHereMarkers} [\'\\]{Identifier}    {
    hop(yytext(), true/*nointerp*/, false/*indented*/);
 }
 "<<~" {MaybeWhsp} {MaybeHereMarkers} [\'\\]{Identifier}    {
    hop(yytext(), true/*nointerp*/, true/*indented*/);
 }

{Identifier} {
    String id = yytext();
    writeSymbol(id, Consts.kwd, yyline);
    maybeIntraState();
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
        maybeIntraState();
}

{Number}        {
    out.write(Sn);
    out.write(yytext());
    out.write("</span>");
    maybeIntraState();
}

 [\"\`] { qop(yytext(), 0, false); }
 \'     { qop(yytext(), 0, true); }
 \#     { saveAndBegin(SCOMMENT); out.write(Sc + "#"); }

 // qq//, qx//, qw//, qr/, tr/// and variants -- all with 2 character names
 ^ {QXRapos} |
 {WxSigils}{QXRapos}   { qop(yytext(), 2, true); } // qx'' qr''
 ^ {QQXRhash} |
 {WxSigils}{QQXRhash}  { qop(yytext(), 2, false); }
 ^ {QQXRPunc} |
 {WxSigils}{QQXRPunc}  { qop(yytext(), 2, false); }
 ^ {QQXRword} |
 {WxSigils}{QQXRword}  { qop(yytext(), 2, false); }

// In Perl these do not actually "interpolate," but "interpolate" for OpenGrok
// xref just means to cross-reference, which is appropriate for qw//.
 ^ {QWhash} |
 {WxSigils}{QWhash}  { qop(yytext(), 2, false); }
 ^ {QWpunc} |
 {WxSigils}{QWpunc}  { qop(yytext(), 2, false); }
 ^ {QWword} |
 {WxSigils}{QWword}  { qop(yytext(), 2, false); }

 ^ {TRhash} |
 {WxSigils}{TRhash}  { qop(yytext(), 2, true); }
 ^ {TRpunc} |
 {WxSigils}{TRpunc}  { qop(yytext(), 2, true); }
 ^ {TRword} |
 {WxSigils}{TRword}  { qop(yytext(), 2, true); }

 // q//, m//, s//, y// and variants -- all with 1 character names
 ^ {MSapos} |
 {WxSigils}{MSapos}  { qop(yytext(), 1, true); } // m'' s''
 ^ {MShash} |
 {WxSigils}{MShash}  { qop(yytext(), 1, false); }
 ^ {MSpunc} |
 {WxSigils}{MSpunc}  { qop(yytext(), 1, false); }
 ^ {MSword} |
 {WxSigils}{MSword}  { qop(yytext(), 1, false); }
 ^ {QYhash} |
 {WxSigils}{QYhash}  { qop(yytext(), 1, true); }
 ^ {QYpunc} |
 {WxSigils}{QYpunc}  { qop(yytext(), 1, true); }
 ^ {QYword} |
 {WxSigils}{QYword}  { qop(yytext(), 1, true); }

 ^ {Pods}   { saveAndBegin(POD); out.write(Sc + yytext()); }
}

<YYINITIAL> {
    "/"    {
        qop(false, "/", 0, false);
        out.write(Ss);
        out.write(yytext());
    }
}

<YYINITIAL, INTRA> {
    // Use some heuristics to identify double-slash syntax for the m//
    // operator. We can't handle all possible appearances of `//', because the
    // first slash cannot always be distinguished from division (/) without
    // true parsing.

    {Mpunc1YYIN} \s* "/"    { hqopPunc(yytext()); }
}

<INTRA> {
    // Continue with more punctuation heuristics

    {Mpunc2IN} \s* "/"    { hqopPunc(yytext()); }
}

<YYINITIAL, INTRA> {
    // Define keyword heuristics

    ^ {Mwords} \s* "/"    {
        hqopSymbol(yytext());
    }
    {WxSigils}{Mwords} \s* "/"    {
        String capture = yytext();
        String boundary = capture.substring(0, 1);
        out.write(htmlize(boundary));
        hqopSymbol(capture.substring(1));
    }
}

<YYINITIAL, INTRA, QUO, QUOxL, HERE, HEREin> {
    {Sigils} {Identifier} {
        //we ignore keywords if the identifier starts with a sigil ...
        String id = yytext().substring(1);
        out.write(yytext().substring(0,1));
        writeSymbol(id, null, yyline);
        maybeIntraState();
    }
}

<QUO, QUOxN, QUOxL, QUOxLxN> {
    \\[\&\<\>\"\']    { out.write(htmlize(yytext())); }
    \\ \S    { out.write(yytext()); }
    {Quo0} |
    \w    {
        String capture = yytext();
        out.write(htmlize(capture));
        if (isQuoteEnding(capture)) {
            restoreState();
            out.write(_S);
        }
    }
}

<QUO, QUOxN, QUOxL, QUOxLxN, HERE, HERExN, HEREin, HEREinxN> {
    {WhiteSpace}{EOL} |
    {EOL} {
        out.write(_S);
        startNewLine();
        out.write(Ss);
    }
}

<POD> {
^ {PodEND} [^\n]* / {EOL} {
    restoreState();
    out.write(yytext() + _S);
  }
}

<SCOMMENT> {
  {WhiteSpace}{EOL} |
  {EOL} {
    restoreState();
    out.write(_S);
    startNewLine();
  }
}

<YYINITIAL, INTRA, SCOMMENT, POD, QUO, QUOxN, QUOxL, QUOxLxN, HERE, HERExN, HEREin, HEREinxN> {
 [&<>\"\']    { out.write(htmlize(yytext())); maybeIntraState(); }
 {WhiteSpace}{EOL} |
 {EOL}          { startNewLine(); }

 {WhiteSpace}   { out.write(yytext()); }
 [!-~]          { out.write(yycharat(0)); maybeIntraState(); }
 [^\n]          { writeUnicodeChar(yycharat(0)); maybeIntraState(); }
}

// "string links" and "comment links"
<SCOMMENT, POD, QUO, QUOxN, HERE, HERExN, HEREin, HEREinxN> {
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
          appendLink(yytext());
        }

{FNameChar}+ "@" {FNameChar}+ "." {FNameChar}+
        {
          writeEMailAddress(yytext());
        }
}
