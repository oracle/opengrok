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
    void maybeIntraState();
    void write(String value) throws IOException;
    void writeHtmlized(String value) throws IOException;

    /**
     * Passes a text fragment that is syntactically a symbol for write
     * processing
     * @param value the excised symbol
     * @param captureOffset the offset from yychar where {@code value} began
     * @param ignoreKwd a value indicating whether keywords should be ignored
     */
    void writeSymbol(String value, int captureOffset, boolean ignoreKwd)
            throws IOException;

    /**
     * Indicates that something unusual happened where normally a symbol would
     * have been written.
     */
    void skipSymbol();

    /**
     * Passes a text fragment that is syntactically a keyword symbol for write
     * processing
     * @param value the excised symbol
     */
    void writeKeyword(String value) throws IOException;

    void doStartNewLine() throws IOException;

    /**
     * Indicates that a premature end of quoting occurred. Everything up to the
     * causal character has been written, and anything following will be
     * indicated via {@link pushback}.
     */
    void abortQuote() throws IOException;

    /**
     * Pushes back to the scanner a specified number of characters
     * @param numChars
     */
    void pushback(int numChars);
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

    /**
     * Gets a value indicating if the quote should be ended, recognizing
     * quote-like operators which allow nesting to increase the nesting level
     * if appropriate.
     * <p>
     * Calling this method has side effects to possibly modify {@code nqchar},
     * {@code waitq}, or {@code endqchar}.
     * @return true if the quote state should end
     */
    public boolean isQuoteEnding(String match) {
        char c = match.charAt(0);
        if (c == endqchar) {
            if (--nendqchar <= 0) {
                if (--nsections <= 0) {
                    endqchar = '\0';
                    nestqchar = '\0';
                    return true;
                } else if (nestqchar != '\0') {
                    waitq = true;
                } else {
                    nendqchar = 1;
                }
            }
        } else if (nestqchar != '\0' && c == nestqchar) {
            if (waitq) {
                waitq = false;
                nendqchar = 1;
            } else {
                ++nendqchar;
            }
        }
        return false;
    }

    /**
     * Gets a value indicating if modifiers are OK at the end of the last
     * quote-like operator.
     * @return true if modifiers are OK
     */
    public boolean areModifiersOK() {
        switch (qopname) {
            case "m":
            case "qr":
            case "s":
            case "tr":
            case "y":
                return true;
            default:
                return false;
        }
    }

    /**
     * Starts a quote-like operator as specified in a syntax fragment, `op',
     * and write the operator to output.
     */
    public void qop(String op, int namelength, boolean nointerp)
        throws IOException {
        qop(true, op, namelength, nointerp);
    }

    /**
     * Starts a quote-like operator as specified in a syntax fragment, `op',
     * and write the operator to output if `doWrite` is true.
     */
    public void qop(boolean doWrite, String capture, int namelength,
        boolean nointerp) throws IOException {
        // If namelength is positive, allow that a non-zero-width word boundary
        // character may have needed to be matched since jflex does not conform
        // with \b as a zero-width simple word boundary. Excise it into
        // `boundary'.
        String boundary = "";
        String postboundary = capture;
        qopname = "";
        if (namelength > 0) {
            postboundary = capture.replaceFirst("^\\W+", "");
            boundary = capture.substring(0, capture.length() -
                postboundary.length());
            qopname = postboundary.substring(0, namelength);
        }
        waitq = false;
        nendqchar = 1;

        switch (qopname) {
            case "tr":
            case "s":
            case "y":
                nsections = 2;
                break;
            default:
                nsections = 1;
                break;
        }

        String postop = postboundary.substring(qopname.length());
        String ltpostop = postop.replaceFirst("^\\s+", "");
        char opc = ltpostop.charAt(0);
        setEndQuoteChar(opc);
        setState(ltpostop, nointerp);

        if (doWrite) {
            listener.writeHtmlized(boundary);
            if (qopname.length() > 0) {
                listener.writeSymbol(qopname, boundary.length(), false);
            } else {
                listener.skipSymbol();
            }
            listener.write(Consts.SS);
            listener.writeHtmlized(postop);
        }
    }

    /** Sets the jflex state reflecting `ltpostop' and `nointerp'. */
    public void setState(String ltpostop, boolean nointerp) {
        int state;
        boolean nolink = false;

        // "no link" for {FNameChar} or {URIChar}, which covers everything in
        // the rule for "string links" below
        if (ltpostop.matches("^[a-zA-Z0-9_]") ||
            ltpostop.matches("^[\\?\\#\\+%&:/\\.@_;=\\$,\\-!~\\*\\\\]")) {
            nolink = true;
        }

        if (nointerp) {
            state = nolink ? QUOxLxN : QUOxN;
        } else {
            state = nolink ? QUOxL : QUO;
        }
        listener.maybeIntraState();
        listener.pushState(state);
    }

    /**
     * Sets a special `endqchar' if appropriate for `opener' or just tracks
     * `opener'.
     */
    private void setEndQuoteChar(char opener) {
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

    /**
     * Begins a quote-like state for a heuristic match of the shorthand // of
     * m// where the `capture' ends with "/", begins with punctuation, and the
     * intervening whitespace may contain LFs -- and writes the parts to output.
     */
    public void hqopPunc(String capture) throws IOException {
        // `preceding' is everything before the '/'; 'lede' is the initial part
        // before any whitespace; and `intervening' is any whitespace.
        String preceding = capture.substring(0, capture.length() - 1);
        String lede = preceding.replaceFirst("\\s+$", "");
        String intervening = preceding.substring(lede.length());

        // OK to pass a fake "m/" with doWrite=false
        qop(false, "m/", 1, false);
        listener.writeHtmlized(lede);
        writeWhitespace(intervening);
        listener.write(Consts.SS);
        listener.write("/");
    }

    /**
     * Begins a quote-like state for a heuristic match of the shorthand // of
     * m// where the `capture' ends with "/", begins with an initial symbol,
     * and the intervening whitespace may contain LFs -- and writes the parts
     * to output.
     */
    public void hqopSymbol(String capture) throws IOException {
        // `preceding' is everything before the '/'; 'lede' is the initial part
        // before any whitespace; and `intervening' is any whitespace.
        String preceding = capture.substring(0, capture.length() - 1);
        String lede = preceding.replaceFirst("\\s+$", "");
        String intervening = preceding.substring(lede.length());

        // OK to pass a fake "m/" with doWrite=false
        qop(false, "m/", 1, false);
        listener.writeSymbol(lede, 0, false);
        writeWhitespace(intervening);
        listener.write(Consts.SS);
        listener.write("/");
    }

    /**
     * Write `whsp' to output -- if it does not contain any LFs then the full
     * String is written; otherwise, pre-LF spaces are condensed as usual.
     */
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

    /** Begins a Here-document state, and writes the `capture' to output. */
    public void hop(String capture, boolean nointerp, boolean indented)
        throws IOException {

        listener.writeHtmlized(capture);

        hereTerminator = null;
        Matcher m = HERE_TERMINATOR_MATCH.matcher(capture);
        if (!m.find()) return;
        hereTerminator = m.group(0);

        int state;
        if (nointerp) {
            state = indented ? HEREinxN : HERExN;
        } else {
            state = indented ? HEREin : HERE;
        }
        listener.maybeIntraState();
        listener.pushState(state);
        listener.write(Consts.SS);
    }

    /**
     * Writes the `capture' to output, possibly ending the Here-document state
     * just beforehand.
     * @return true if the quote state ended
     */
    public boolean maybeEndHere(String capture) throws IOException {
        if (!isHereEnding(capture)) {
            listener.writeHtmlized(capture);
            return false;
        } else {
            listener.popState();
            listener.write(Consts.ZS);
            listener.writeHtmlized(capture);
            return true;
        }
    }

    /**
     * Gets a value indicating if the Here-document should be ended.
     * @return true if the quote state should end
     */
    public boolean isHereEnding(String capture) {
        String trimmed = capture.replaceFirst("^\\s+", "");
        return trimmed.equals(hereTerminator);
    }

    /**
     * Splits a sigil identifier -- where the `capture' starts with
     * a sigil and ends in an identifier and where Perl allows whitespace after
     * the sigil -- and write the parts to output.
     * <p>
     * Seeing the {@link endqchar} in the capture will affect an active
     * quote-like operator.
     */
    public void sigilID(String capture) throws IOException {
        String sigil = capture.substring(0, 1);

        if (capture.charAt(0) == endqchar) {
            listener.skipSymbol();
            listener.writeHtmlized(sigil);
            if (isQuoteEnding(sigil)) listener.abortQuote();
            listener.pushback(capture.length() - 1);
            return;
        }

        String postsigil = capture.substring(1);
        String id = postsigil.replaceFirst("^\\s+", "");
        String s0 = postsigil.substring(0, postsigil.length() - id.length());

        int ohnooo;
        if ((ohnooo = id.indexOf(endqchar)) == -1) {
            listener.writeHtmlized(sigil);
            listener.write(s0);
            listener.writeSymbol(id, sigil.length() + s0.length(), true);
        } else {
            // If the identifier contains the end quoting character, then it
            // may or may not parse in Perl. Treat everything before the first
            // instance of the `endqchar' as inside the quote -- and possibly
            // as real identifier -- and possibly abort the quote early.
            // e.g.: qr z$abcz;
            //     OK
            // e.g.: qr z$abziz;
            //     Unknown regexp modifier "/z" at -e line 1, near "= "
            //     Global symbol "$ab" requires explicit package name ...
            // e.g.: qr i$abixi;
            //     OK
            String w0 = id.substring(0, ohnooo);
            String p0 = id.substring(ohnooo, ohnooo + 1);
            String w1 = id.substring(ohnooo + 1);
            listener.writeHtmlized(sigil);
            listener.write(s0);
            if (w0.length() > 0) {
                listener.writeSymbol(w0, sigil.length() + s0.length(), true);
            } else {
                listener.skipSymbol();
            }
            listener.writeHtmlized(p0);
            if (isQuoteEnding(p0)) listener.abortQuote();
            listener.pushback(w1.length());
        }
    }

    /**
     * Splits a braced sigil identifier -- where the `capture' starts with
     * a sigil and ends with a '}' and where Perl allows whitespace after the
     * sigil and around the identifier -- and write the parts to output.
     */
    public void bracedSigilID(String capture) throws IOException {
        // $      {      identifier      }
        //
        // S|_________interior0_________|r
        //        |_____ltinterior0_____|r
        //        l|_____interior1______|r
        //               |_ltinterior1__|r
        // S|_s0_|l|_s1_||---id---||_s2_|r

        String sigil = capture.substring(0, 1);
        String rpunc = capture.substring(capture.length() - 1);
        String interior0 = capture.substring(1, capture.length() - 1);
        String ltinterior0 = interior0.replaceFirst("^\\s+", "");
        String s0 = interior0.substring(0, interior0.length() -
            ltinterior0.length());

        String lpunc = ltinterior0.substring(0, 1);
        String interior1 = ltinterior0.substring(1);
        String ltinterior1 = interior1.replaceFirst("^\\s+", "");
        String s1 = interior1.substring(0, interior1.length() -
            ltinterior1.length());

        String s2 = ltinterior1.replaceFirst("^\\S+", "");
        String id = ltinterior1.substring(0, ltinterior1.length() -
            s2.length());

        listener.writeHtmlized(sigil);
        listener.write(s0);
        listener.writeHtmlized(lpunc);
        listener.write(s1);
        listener.writeSymbol(id, sigil.length() + s0.length() +
            lpunc.length() + s1.length(), true);
        listener.write(s2);
        listener.writeHtmlized(rpunc);
    }

    /**
     * Write a special identifier as a keyword -- unless {@link endqchar} is in
     * the {@code capture}, which will affect an active quote-like operator
     * instead.
     */
    public void specialID(String capture) throws IOException {
        if (capture.indexOf(endqchar) == -1) {
            listener.writeKeyword(capture);
        } else {
            for (int i = 0; i < capture.length(); ++i) {
                char c = capture.charAt(i);
                String w = new String(new char[] {c});
                listener.writeHtmlized(w);
                if (isQuoteEnding(w)) {
                    listener.abortQuote();
                    listener.pushback(capture.length() - i - 1);
                    break;
                }
            }
        }
    }

    private final static Pattern HERE_TERMINATOR_MATCH = Pattern.compile(
        "[a-zA-Z0-9_]+$");

    /**
     * When matching a quoting construct like qq[], q(), m//, s```, etc., the
     * operator name (e.g., "m" or "tr") is stored. Unlike {@code endqchar} it
     * is not unset when the quote ends, because it is useful to indicate if
     * quote modifier characters are expected.
     */
    private String qopname;

    /**
     * When matching a quoting construct like qq[], q(), m//, s```, etc., the
     * terminating character is stored.
     */
    private char endqchar;

    /**
     * When matching a quoting construct like qq[], q(), m&lt;&gt;, s{}{} that
     * nest, the begin character ('[', '&lt;', '(', or '{') is stored so that
     * nesting is tracked and {@code nendqchar} is incremented appropriately.
     * Otherwise, {@code nestqchar} is set to '\0' if no nesting occurs.
     */
    private char nestqchar;

    /**
     * When matching a quoting construct like qq//, m//, or s```, etc., the
     * number of remaining end separators in an operator section is stored.
     * It starts at 1, and nesting increases the value when seen.
     */
    private int nendqchar;

    /**
     * When matching a quoting construct like qq//, m//, or s```, etc., the
     * number of sections for the operator is stored. E.g., for m//, it is 1;
     * for tr/// it is 2.
     */
    private int nsections;

    /**
     * When matching a two part construct like tr/// or s```, etc., after the
     * first end separator, {@code waitq} is set to TRUE so that nesting is not
     * active.
     */
    private boolean waitq;

    /** Stores the terminating identifier for For Here-documents */
    private String hereTerminator;
}
