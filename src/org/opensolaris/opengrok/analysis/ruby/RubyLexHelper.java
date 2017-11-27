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

package org.opensolaris.opengrok.analysis.ruby;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.analysis.Resettable;
import org.opensolaris.opengrok.util.StringUtils;
import org.opensolaris.opengrok.web.HtmlConsts;

/**
 * Represents an API for object's using {@link RubyLexHelper}
 */
interface RubyLexListener {
    void yypush(int state);
    void yypop() throws IOException;
    void yybegin(int state);
    void yypushback(int numChars);
    void maybeIntraState();

    void take(String value) throws IOException;
    void takeNonword(String value) throws IOException;

    /**
     * Passes a text fragment that is syntactically a symbol for processing.
     * @param value the excised symbol
     * @param captureOffset the offset from yychar where {@code value} began
     * @param ignoreKwd a value indicating whether keywords should be ignored
     * @return true if the {@code value} was not in keywords or if the
     * {@code ignoreKwd} was true
     */
    boolean takeSymbol(String value, int captureOffset, boolean ignoreKwd)
        throws IOException;

    /**
     * Indicates that something unusual happened where normally a symbol would
     * have been written.
     */
    void skipSymbol();

    /**
     * Passes a text fragment that is syntactically a keyword symbol for
     * processing
     * @param value the excised symbol
     */
    void takeKeyword(String value) throws IOException;

    /**
     * Indicates that the current line is ended.
     *
     * @throws IOException thrown on error when handling the EOL
     */
    void startNewLine() throws IOException;
}

/**
 * Represents a helper for Ruby lexers
 */
class RubyLexHelper implements Resettable {

    // Using equivalent of {Local_nextchar} from RubyProductions.lexh
    private final static Pattern HERE_TERMINATOR_MATCH = Pattern.compile(
        "^[a-zA-Z0-9_\u00160-\u0255]+");

    private final RubyLexListener listener;

    private final int QUOxLxN;
    private final int QUOxN;
    private final int QUOxL;
    private final int QUO;
    private final int HEREinxN;
    private final int HERExN;
    private final int HEREin;
    private final int HERE;

    private Queue<HereDocSettings> hereSettings;

    /**
     * When matching a quoting construct like qq[], q(), m//, s```, etc., the
     * operator name (e.g., "m" or "tr") is stored. Unlike {@code endqchar} it
     * is not unset when the quote ends, because it is useful to indicate if
     * quote modifier characters are expected.
     */
    private String qopname;

    /**
     * When matching a quoting construct like %w(), '', %[], etc., the
     * terminating character is stored.
     */
    private char endqchar;

    /**
     * When matching a quoting construct like %[], %w(), %&lt;&gt; etc. that
     * nest, the begin character ('[', '&lt;', '(', or '{') is stored so that
     * nesting is tracked and {@code nendqchar} is incremented appropriately.
     * Otherwise, {@code nestqchar} is set to '\0' if no nesting occurs.
     */
    private char nestqchar;

    /**
     * When matching a quoting construct like %[], %w(), etc., the
     * number of remaining end separators is stored. It starts at 1, and
     * any nesting increases the value.
     */
    private int nendqchar;

    /**
     * When interpolating inside a quoting construct, the number of remaining
     * '}' is stored. It starts at 1, and any nesting increases the value.
     */
    private int nendbrace;

    public RubyLexHelper(int qUO, int qUOxN, int qUOxL, int qUOxLxN,
	    RubyLexListener listener,
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
     * Resets the instance to an initial state.
     */
    @Override
    public void reset() {
        endqchar = '\0';
        if (hereSettings != null) hereSettings.clear();
        nendbrace = 0;
        nendqchar = 0;
        nestqchar = '\0';
        qopname = null;
    }

    /**
     * Determine if the quote should end based on the first character of
     * {@code capture}, recognizing quote-like operators that allow nesting to
     * increase the nesting level if appropriate.
     * <p>
     * Calling this method has side effects to possibly modify {@code nqchar},
     * {@code waitq}, or {@code endqchar}.
     * @return true if the quote state should end
     */
    public boolean maybeEndQuote(String capture) {
        char c = capture.charAt(0);
        if (c == endqchar) {
            if (--nendqchar <= 0) {
                endqchar = '\0';
                nestqchar = '\0';
                return true;
            }
        } else if (nestqchar != '\0' && c == nestqchar) {
            ++nendqchar;
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
            case "m": // named here a la Perl for the Ruby /pat/ operator
                return true;
            default:
                return false;
        }
    }

    /**
     * Starts a quote-like operator as specified in a syntax fragment,
     * {@code op}, and gives the {@code op} for the {@code listener} to take.
     */
    public void qop(String op, int namelength, boolean nointerp)
        throws IOException {
        qop(true, op, namelength, nointerp);
    }

    /**
     * Starts a quote-like operator as specified in a syntax fragment,
     * {@code op}, and gives the {@code capture} for the {@code listener} to
     * take if {@code doWrite} is true.
     */
    public void qop(boolean doWrite, String capture, int namelength,
        boolean nointerp) throws IOException {
        // If namelength is positive, allow that a non-zero-width word boundary
        // character may have needed to be matched since jflex does not conform
        // with \b as a zero-width simple word boundary. Excise it into
        // `boundary'.
        String postop = capture;
        qopname = "";
        if (namelength > 0) {
            qopname = capture.substring(0, namelength);
            postop = capture.substring(qopname.length());
        }
        nendqchar = 1;

        char opc = postop.charAt(0);
        setEndQuoteChar(opc);
        setState(postop, nointerp);

        if (doWrite) {
            listener.takeNonword(qopname);
            listener.skipSymbol();
            listener.take(HtmlConsts.SPAN_S);
            listener.takeNonword(postop);
        }
    }

    /**
     * Sets the jflex state reflecting {@code postop} and {@code nointerp}.
     */
    public void setState(String postop, boolean nointerp) {
        int state;
        boolean nolink = false;

        // "no link" for values in the rules for "string links" if `postop'
        // is file- or URI-like.
        if (StringUtils.startsWithFnameChars(postop) ||
            StringUtils.startsWithURIChars(postop)) {
            nolink = true;
        }

        if (nointerp) {
            state = nolink ? QUOxLxN : QUOxN;
        } else {
            state = nolink ? QUOxL : QUO;
        }
        listener.maybeIntraState();
        listener.yypush(state);
    }

    /**
     * Sets a special {@code endqchar} if appropriate for {@code opener} or
     * just tracks {@code opener} as {@code endqchar}.
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
     * m// where the {@code capture} ends with "/", begins with punctuation,
     * and the intervening whitespace may contain LFs -- and writes the parts
     * to output.
     */
    public void hqopPunc(String capture) throws IOException {
        // `preceding' is everything before the '/'; 'lede' is the initial part
        // before any whitespace; and `intervening' is any whitespace.
        String preceding = capture.substring(0, capture.length() - 1);
        String lede = preceding.replaceFirst("\\s+$", "");
        String intervening = preceding.substring(lede.length());

        // OK to pass a fake "m/" with doWrite=false
        qop(false, "m/", 1, false);
        listener.takeNonword(lede);
        takeWhitespace(intervening);
        listener.take(HtmlConsts.SPAN_S);
        listener.take("/");
    }

    /**
     * Begins a quote-like state for a heuristic match of the shorthand // of
     * m// where the {@code capture} ends with "/", begins with an initial
     * symbol, and the intervening whitespace may contain LFs -- and writes the
     * parts to output.
     */
    public void hqopSymbol(String capture) throws IOException {
        // `preceding' is everything before the '/'; 'lede' is the initial part
        // before any whitespace; and `intervening' is any whitespace.
        String preceding = capture.substring(0, capture.length() - 1);
        String lede = preceding.replaceFirst("\\s+$", "");
        String intervening = preceding.substring(lede.length());

        // OK to pass a fake "m/" with doWrite=false
        qop(false, "m/", 1, false);
        listener.takeSymbol(lede, 0, false);
        takeWhitespace(intervening);
        listener.take(HtmlConsts.SPAN_S);
        listener.take("/");
    }

    /**
     * Write {@code whsp} to output -- if it does not contain any LFs then the
     * full String is written; otherwise, pre-LF spaces are condensed as usual.
     */
    private void takeWhitespace(String whsp) throws IOException {
        int i;
        if ((i = whsp.indexOf("\n")) == -1) {
            listener.take(whsp);
        } else {
            int numlf = 1, off = i + 1;
            while ((i = whsp.indexOf("\n", off)) != -1) {
                ++numlf;
                off = i + 1;
            }
            while (numlf-- > 0) listener.startNewLine();
            if (off < whsp.length()) listener.take(whsp.substring(off));
        }
    }

    /**
     * Parses a Here-document declaration, and takes the {@code capture} using
     * {@link RubyLexListener#takeNonword(java.lang.String)}. If the
     * declaration is valid, {@code hereSettings} will have been appended.
     */
    public void hop(String capture) throws IOException {
        if (!capture.startsWith("<<")) {
            throw new IllegalArgumentException("bad HERE: " + capture);
        }

        listener.takeNonword(capture);
        if (hereSettings == null) hereSettings = new LinkedList<>();

        String remaining = capture;
        int i = 0;
        HereDocSettings settings;
        boolean indented = false;
        boolean nointerp;
        String terminator;

        String opener = remaining.substring(0, i + 2);
        remaining = remaining.substring(opener.length());
        if (remaining.startsWith("~") || remaining.startsWith("-")) {
            indented = true;
            remaining = remaining.substring(1);
        }

        char c = remaining.charAt(0);
        switch (c) {
            case '\'':
                nointerp = true;
                remaining = remaining.substring(1);
                break;
            case '`':
                // (Ruby, unlike Perl, does not recognize '"' here.)
                nointerp = false;
                remaining = remaining.substring(1);
                break;
            default:
                c = '\0';
                nointerp = false;
                break;
        }

        if (c != '\0') {
            if ((i = remaining.indexOf(c)) < 1) {
                terminator = remaining;
            } else {
                terminator = remaining.substring(0, i);
            }
        } else {
            Matcher m = HERE_TERMINATOR_MATCH.matcher(remaining);
            if (!m.find()) return;
            terminator = m.group(0);
        }

        int state;
        if (nointerp) {
            state = indented ? HEREinxN : HERExN;
        } else {
            state = indented ? HEREin : HERE;
        }
        settings = new HereDocSettings(terminator, state);
        hereSettings.add(settings);
    }

    /**
     * Pushes the first Here-document state if any declarations were parsed, or
     * else does nothing.
     * @return true if a Here state was pushed
     */
    public boolean maybeStartHere() throws IOException {
        if (hereSettings != null && hereSettings.size() > 0) {
            HereDocSettings settings = hereSettings.peek();
            listener.yypush(settings.state);
            listener.take(HtmlConsts.SPAN_S);
            return true;
        }
        return false;
    }

    /**
     * Process the {@code capture}, possibly ending the Here-document state
     * just beforehand.
     * @return true if the quote state ended
     */
    public boolean maybeEndHere(String capture) throws IOException {
        String trimmed = capture.replaceFirst("^\\s+", "");
        HereDocSettings settings = hereSettings.peek();

        boolean didZspan = false;
        if (trimmed.equals(settings.terminator)) {
            listener.take(HtmlConsts.ZSPAN);
            didZspan = true;
            hereSettings.remove();
        }

        listener.takeNonword(capture);

        if (hereSettings.size() > 0) {
            settings = hereSettings.peek();
            listener.yybegin(settings.state);
            if (didZspan) listener.take(HtmlConsts.SPAN_S);
            return false;
        } else {
            listener.yypop();
            return true;
        }
    }

    /**
     * Resets the interpolation counter to 1.
     */
    public void interpop() {
        nendbrace = 1;
    }

    /**
     * Determine if the interpolation should end based on the first character
     * of {@code capture}, recognizing tokens that increase the nesting level
     * instead.
     * <p>
     * Calling this method has side effects to possibly modify
     * {@code nendbrace}.
     * @return true if the interpolation state should end
     */
    public boolean maybeEndInterpolation(String capture) {
        if (nendbrace <= 0) {
            return false;
        }
        if (capture.startsWith("}")) {
            if (--nendbrace <= 0) {
                return true;
            }
        } else if (capture.startsWith("{")) {
            ++nendbrace;
        }
        return false;
    }

    /**
     * Take a series of module names separated by "::".
     */
    public void takeModules(String capture) throws IOException {
        final String SEP = "::";
        int o = 0, i;
        while (o < capture.length() && (i = capture.indexOf(SEP, o)) != -1) {
            String module = capture.substring(o, i);
            listener.takeSymbol(module, o, false); 
            listener.takeNonword(SEP);
            o = i + 2;
        }
        if (o < capture.length()) {
            String module = capture.substring(o);
            listener.takeSymbol(module, o, false); 
        }
    }

    /**
     * Subtract the number of initial, non-word characters from the length of
     * {@code capture}.
     * @param capture a defined value
     * @return the length of {@code value} minus the number of initial,
     * non-word characters
     */
    public int nameLength(String capture) {
        int len = capture.length();
        for (int i = 0; i < capture.length(); ++i) {
            if (Character.isLetterOrDigit(capture.charAt(i))) break;
            --len;
        }
        return len;
    }

    private class HereDocSettings {
        private final String terminator;
        private final int state;

        public HereDocSettings(String terminator, int state) {
            this.terminator = terminator;
            this.state = state;
        }

        public String getTerminator() { return terminator; }

        public int getState() { return state; }
    }
}
