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

package org.opengrok.indexer.analysis.ruby;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.analysis.Resettable;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;
import org.opengrok.indexer.analysis.JFlexJointLexer;
import org.opengrok.indexer.util.RegexUtils;

/**
 * Represents an API for object's using {@link RubyLexHelper}
 */
interface RubyLexer extends JFlexJointLexer {
    void maybeIntraState();
    void popHelper();
}

/**
 * Represents a helper for Ruby lexers
 */
class RubyLexHelper implements Resettable {

    // Using equivalent of {Local_nextchar} from RubyProductions.lexh
    private final static Pattern HERE_TERMINATOR_MATCH = Pattern.compile(
        "^[a-zA-Z0-9_\u00160-\u0255]+");

    private final RubyLexer lexer;

    private final int QUOxLxN;
    private final int QUOxN;
    private final int QUOxL;
    private final int QUO;
    private final int HEREinxN;
    private final int HERExN;
    private final int HEREin;
    private final int HERE;
    private final int SCOMMENT;
    private final int POD;

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

    /**
     * When matching a quoting construct, a Pattern to identify collateral
     * capture characters is stored.
     */
    private Pattern collateralCapture;

    public RubyLexHelper(int qUO, int qUOxN, int qUOxL, int qUOxLxN, RubyLexer lexer,
        int hERE, int hERExN, int hEREin, int hEREinxN, int sCOMMENT,
        int pOD) {
        if (lexer == null) {
            throw new IllegalArgumentException("`lexer' is null");
        }
        this.lexer = lexer;
        this.QUOxLxN = qUOxLxN;
        this.QUOxN = qUOxN;
        this.QUOxL = qUOxL;
        this.QUO = qUO;
        this.HEREinxN = hEREinxN;
        this.HERExN = hERExN;
        this.HEREin = hEREin;
        this.HERE = hERE;
        this.SCOMMENT = sCOMMENT;
        this.POD = pOD;
    }

    /**
     * Resets the instance to an initial state.
     */
    @Override
    public void reset() {
        collateralCapture = null;
        endqchar = '\0';
        if (hereSettings != null) {
            hereSettings.clear();
        }
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

        // N.b. the following will write anyway -- despite any `doWrite'
        // setting -- if interpolation is truly ending, but that is OK as a
        // quote-like operator is not starting in that case.
        if (maybeEndInterpolation(capture)) {
            return;
        }

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
        collateralCapture = null;

        char opc = postop.charAt(0);
        setEndQuoteChar(opc);
        setState(postop, nointerp);

        if (doWrite) {
            lexer.offer(qopname);
            lexer.skipSymbol();
            lexer.disjointSpan(HtmlConsts.STRING_CLASS);
            lexer.offer(postop);
        }
    }

    /**
     * Sets the jflex state reflecting {@code postop} and {@code nointerp}.
     */
    public void setState(String postop, boolean nointerp) {
        int state;
        boolean nolink = false;

        // "no link" for values in the rules for "string links" if `postop'
        // starts path-like or with the e-mail delimiter.
        if (StringUtils.startsWithFpathChar(postop) ||
            postop.startsWith("@")) {
            nolink = true;
        }

        if (nointerp) {
            state = nolink ? QUOxLxN : QUOxN;
        } else {
            state = nolink ? QUOxL : QUO;
        }
        lexer.maybeIntraState();
        lexer.yypush(state);
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
        if (maybeEndInterpolation(capture)) {
            return;
        }

        // `preceding' is everything before the '/'; 'lede' is the initial part
        // before any whitespace; and `intervening' is any whitespace.
        String preceding = capture.substring(0, capture.length() - 1);
        String lede = preceding.replaceFirst("\\s+$", "");
        String intervening = preceding.substring(lede.length());

        // OK to pass a fake "m/" with doWrite=false
        qop(false, "m/", 1, false);
        lexer.offer(lede);
        takeWhitespace(intervening);
        lexer.disjointSpan(HtmlConsts.STRING_CLASS);
        lexer.offer("/");
    }

    /**
     * Begins a quote-like state for a heuristic match of the shorthand // of
     * m// where the {@code capture} ends with "/", begins with an initial
     * symbol, and the intervening whitespace may contain LFs -- and writes the
     * parts to output.
     */
    public void hqopSymbol(String capture) throws IOException {
        if (maybeEndInterpolation(capture)) {
            return;
        }

        // `preceding' is everything before the '/'; 'lede' is the initial part
        // before any whitespace; and `intervening' is any whitespace.
        String preceding = capture.substring(0, capture.length() - 1);
        String lede = preceding.replaceFirst("\\s+$", "");
        String intervening = preceding.substring(lede.length());

        // OK to pass a fake "m/" with doWrite=false
        qop(false, "m/", 1, false);
        lexer.offerSymbol(lede, 0, false);
        takeWhitespace(intervening);
        lexer.disjointSpan(HtmlConsts.STRING_CLASS);
        lexer.offer("/");
    }

    /**
     * Write {@code whsp} to output -- if it does not contain any LFs then the
     * full String is written; otherwise, pre-LF spaces are condensed as usual.
     */
    private void takeWhitespace(String whsp) throws IOException {
        int i;
        if ((i = whsp.indexOf("\n")) == -1) {
            lexer.offer(whsp);
        } else {
            int numlf = 1, off = i + 1;
            while ((i = whsp.indexOf("\n", off)) != -1) {
                ++numlf;
                off = i + 1;
            }
            while (numlf-- > 0) {
                lexer.startNewLine();
            }
            if (off < whsp.length()) {
                lexer.offer(whsp.substring(off));
            }
        }
    }

    /**
     * Parses a Here-document declaration, and takes the {@code capture} using
     * {@link RubyLexer#offer(java.lang.String)}. If the
     * declaration is valid, {@code hereSettings} will have been appended.
     */
    public void hop(String capture) throws IOException {
        if (!capture.startsWith("<<")) {
            throw new IllegalArgumentException("bad HERE: " + capture);
        }

        lexer.offer(capture);
        if (hereSettings == null) {
            hereSettings = new LinkedList<>();
        }

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
            if (!m.find()) {
                return;
            }
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
            lexer.yypush(settings.state);
            lexer.disjointSpan(HtmlConsts.STRING_CLASS);
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
            lexer.disjointSpan(null);
            didZspan = true;
            hereSettings.remove();
        }

        lexer.offer(capture);

        if (hereSettings.size() > 0) {
            settings = hereSettings.peek();
            lexer.yybegin(settings.state);
            if (didZspan) {
                lexer.disjointSpan(HtmlConsts.STRING_CLASS);
            }
            return false;
        } else {
            lexer.yypop();
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
    public boolean maybeEndInterpolation(String capture) throws IOException {
        if (nendbrace <= 0) {
            return false;
        }
        if (capture.startsWith("}")) {
            if (--nendbrace <= 0) {
                int rem = capture.length() - 1;
                String opener = capture.substring(0, 1);
                lexer.popHelper();
                lexer.yypop();
                lexer.disjointSpan(HtmlConsts.STRING_CLASS);
                lexer.offer(opener);
                if (rem > 0) {
                    lexer.yypushback(rem);
                }
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
            lexer.offerSymbol(module, o, false);
            lexer.offer(SEP);
            o = i + 2;
        }
        if (o < capture.length()) {
            String module = capture.substring(o);
            lexer.offerSymbol(module, o, false);
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
            if (Character.isLetterOrDigit(capture.charAt(i))) {
                break;
            }
            --len;
        }
        return len;
    }

    /**
     * Gets a pattern to match the collateral capture for the current quoting
     * state or null if there is no active quoting state.
     * @return a defined pattern or null
     */
    public Pattern getCollateralCapturePattern() {
        if (endqchar == '\0') {
            return null;
        }
        if (collateralCapture != null) {
            return collateralCapture;
        }

        StringBuilder patb = new StringBuilder("[");
        patb.append(Pattern.quote(String.valueOf(endqchar)));
        if (nestqchar != '\0') {
            patb.append(Pattern.quote(String.valueOf(nestqchar)));
        }
        patb.append("]");
        patb.append(RegexUtils.getNotFollowingEscapePattern());
        collateralCapture = Pattern.compile(patb.toString());
        return collateralCapture;
    }

    /**
     * Calls {@link RubyLexer#phLOC()} if the yystate is not SCOMMENT or POD.
     */
    public void chkLOC() {
        int yystate = lexer.yystate();
        if (yystate != SCOMMENT && yystate != POD) {
            lexer.phLOC();
        }
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
