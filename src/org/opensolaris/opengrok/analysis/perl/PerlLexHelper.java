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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.perl;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents an API for object's using {@link PerlLexHelper}
 */
interface PerlLexListener {
    void pushState(int state);
    void popState() throws IOException;
    void write(String value) throws IOException;
    void writeHtmlized(String value) throws IOException;
    void writeSymbol(String value) throws IOException;
    void doStartNewLine() throws IOException;
}

/**
 * Represents a helper for Perl lexers to work around jflex's lack of lex
 * inheritance
 */
class PerlLexHelper {

    private final PerlLexListener listener;

    private final int QUOxLxN;
    private final int QUOxN;
    private final int QUOxL;
    private final int QUO;
    private final int HEREinxN;
    private final int HERExN;
    private final int HEREin;
    private final int HERE;

    public PerlLexHelper(int qUO, int qUOxN, int qUOxL, int qUOxLxN,
        PerlLexListener listener,
        int hERE, int hERExN, int hEREin, int hEREinxN) {
        if (listener == null) {
            throw new IllegalArgumentException("`listener' is null");
        }
        this.listener = listener;
        this.QUOxLxN = qUOxLxN;
        this.QUOxN = qUOxN;
        this.QUOxL = qUOxL;
        this.QUO = qUO;
        this.HEREinxN = hEREinxN;
        this.HERExN = hERExN;
        this.HEREin = hEREin;
        this.HERE = hERE;        
    }

    // Gets a value indicating if the quote should be ended. Also, recognize
    // quote-like operators which allow nesting to increase the nesting level
    // if appropriate.
    public boolean isQuoteEnding(String match)
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
    public void qop(String op, int namelength, boolean nointerp)
        throws IOException
    {
        qop(true, op, namelength, nointerp);
    }

    // Starts a quote-like operator as specified in a syntax fragment, `op',
    // and write the operator to output if `doWrite` is true.
    public void qop(boolean doWrite, String op, int namelength,
        boolean nointerp) throws IOException
    {
        // If namelength is positive, allow that a non-zero-width word boundary
        // character may have needed to be matched since jflex does not conform
        // with \b as a zero-width simple word boundary. Excise it into
        // `boundary'.
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
            listener.writeHtmlized(boundary);
            listener.writeSymbol(opname);
            listener.write(Consts.SS);
            listener.writeHtmlized(postop);
        }
    }

    // Sets the jflex state reflecting `ltpostop' and `nointerp'.
    public void setState(String ltpostop, boolean nointerp)
    {
        int state;
        boolean nolink = false;

        // "no link" for {FNameChar} or {URIChar}, which covers everything in
        // the rule for "string links" below
        if (ltpostop.matches("^[a-zA-Z0-9_]")) {
            nolink = true;

            // it's impossible to have a dynamic pattern in jflex, which would
            // be required to continue to "interpolate OpenGrok links" but
            // exclude the initial character from `ltpostop' -- so just disable
            // it entirely.
            nointerp = true; // override
        } else if (ltpostop.matches("^[\\?\\#\\+%&:/\\.@_;=\\$,\\-!~\\*\\\\]")) {
            nolink = true;
        }

        if (nointerp) {
            state = nolink ? QUOxLxN : QUOxN;
        } else {
            state = nolink ? QUOxL : QUO;
        }
        listener.pushState(state);
    }

    // Sets a special `endqchar' if appropriate for `opener' or just tracks
    // `opener'
    private void setEndQuoteChar(char opener)
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

    // Begins a quote-like state for a heuristic match of the shorthand // of
    // m// where the `capture' ends with "/", begins with punctuation, and the
    // intervening whitespace may contain LFs -- and writes the parts to output.
    public void hqopPunc(String capture) throws IOException
    {
        // `preceding' is everything before the '/'; 'lede' is the initial part
        // before any whitespace; and `intervening' is any whitespace.
        String preceding = capture.substring(0, capture.length() - 1);
        String lede = preceding.replaceAll("\\s+$", "");
        String intervening = preceding.substring(lede.length());

        qop(false, "/", 0, false);
        listener.writeHtmlized(lede);
        writeWhitespace(intervening);
        listener.write(Consts.SS);
        listener.write("/");
    }

    // Begins a quote-like state for a heuristic match of the shorthand // of
    // m// where the `capture' ends with "/", begins with an initial symbol,
    // and the intervening whitespace may contain LFs -- and writes the parts
    // to output.
    public void hqopSymbol(String capture) throws IOException
    {
        // `preceding' is everything before the '/'; 'lede' is the initial part
        // before any whitespace; and `intervening' is any whitespace.
        String preceding = capture.substring(0, capture.length() - 1);
        String lede = preceding.replaceAll("\\s+$", "");
        String intervening = preceding.substring(lede.length());

        qop(false, "/", 0, false);
        listener.writeSymbol(lede);
        writeWhitespace(intervening);
        listener.write(Consts.SS);
        listener.write("/");
    }

    // Write `whsp' to output -- if it does not contain any LFs then the full
    // String is written; otherwise, pre-LF spaces are condensed as usual.
    private void writeWhitespace(String whsp) throws IOException {
        int i;
        if ((i = whsp.indexOf("\n")) == -1) {
            listener.write(whsp);
        } else {
            int numlf = 1, off = i + 1;
            while ((i = whsp.indexOf("\n", off)) != -1) {
                ++numlf;
                off = i + 1;
            }
            while (numlf-- > 0) listener.doStartNewLine();
            if (off < whsp.length()) listener.write(whsp.substring(off));
        }
    }

    // Begins a Here-document state, and writes the `capture' to output.
    public void hop(String capture, boolean nointerp, boolean indented)
        throws IOException {

        int state;

        hereTerminator = null;
        Matcher m = HERE_TERMINATOR_MATCH.matcher(capture);
        if (m.find()) hereTerminator = m.group(0);

        if (nointerp) {
            state = indented ? HEREinxN : HERExN;
        } else {
            state = indented ? HEREin : HERE;
        }
        listener.pushState(state);

        listener.writeHtmlized(capture);
        listener.write(Consts.SS);
    }

    // Writes the `capture' to output, possibly ending the Here-document state
    // just beforehand.
    public void maybeEndHere(String capture) throws IOException {
        if (!isHereEnding(capture)) {
            listener.writeHtmlized(capture);
        } else {
            listener.popState();
            listener.write(Consts.ZS);
            listener.writeHtmlized(capture);
        }
    }

    // Gets a value indicating if the Here-document should be ended.
    public boolean isHereEnding(String capture)
    {
        String trimmed = capture.replaceAll("^\\s+", "");
        return trimmed.equals(hereTerminator);
    }

    private final static Pattern HERE_TERMINATOR_MATCH = Pattern.compile(
        "[a-zA-Z0-9_]+$");

    // When matching a quoting construct like qq[], q(), m//, s```, etc., the
    // terminating character is stored
    private char endqchar;

    // When matching a quoting construct like qq[], q(), m<>, s{}{} that nest,
    // the begin character ('[', '<', '(', or '{') is stored so that nesting
    // is tracked and nqchar is incremented appropriately. Otherwise,
    // `nestqchar' is set to '\0' if no nesting occurs.
    private char nestqchar;

    // When matching a quoting construct like qq//, m//, or s```, etc., the
    // number of remaining end separators in the operator is stored. E.g., for
    // m//, it is 1; for tr/// it is 2. Nesting increases the value when seen.
    private int nqchar;

    // When matching a two part construct like tr/// or s```, etc., after the
    // first end separator, `waitingqchar' is set to TRUE so that nesting is not
    // active.
    private boolean waitq;

    // Stores the terminating identifier for For Here-documents
    private String hereTerminator;
}
