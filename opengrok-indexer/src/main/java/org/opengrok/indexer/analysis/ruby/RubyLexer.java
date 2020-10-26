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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.ruby;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opengrok.indexer.analysis.JFlexJointLexer;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.Resettable;
import org.opengrok.indexer.util.RegexUtils;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.web.HtmlConsts;

/**
 * Represents an abstract base class for Ruby lexers.
 */
@SuppressWarnings("Duplicates")
abstract class RubyLexer extends JFlexSymbolMatcher
        implements JFlexJointLexer, Resettable {

    // Using equivalent of {Local_nextchar} from RubyProductions.lexh
    private static final Pattern HERE_TERMINATOR_MATCH = Pattern.compile(
        "^[a-zA-Z0-9_\u00160-\u0255]+");

    private RubyLexerData dHead;

    private Stack<RubyLexerData> data;

    RubyLexer() {
        dHead = new RubyLexerData();
    }

    /**
     * Resets the instance to an initial state.
     */
    @Override
    public void reset() {
        super.reset();
        dHead = new RubyLexerData();
        if (data != null) {
            data.clear();
        }
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
        if (c == dHead.endqchar) {
            if (--dHead.nendqchar <= 0) {
                dHead.endqchar = '\0';
                dHead.nestqchar = '\0';
                return true;
            }
        } else if (dHead.nestqchar != '\0' && c == dHead.nestqchar) {
            ++dHead.nendqchar;
        }
        return false;
    }

    /**
     * Gets a value indicating if modifiers are OK at the end of the last
     * quote-like operator.
     * @return true if modifiers are OK
     */
    public boolean areModifiersOK() {
        switch (dHead.qopname) {
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
        dHead.qopname = "";
        if (namelength > 0) {
            dHead.qopname = capture.substring(0, namelength);
            postop = capture.substring(dHead.qopname.length());
        }
        dHead.nendqchar = 1;
        dHead.collateralCapture = null;

        char opc = postop.charAt(0);
        setEndQuoteChar(opc);
        setState(postop, nointerp);

        if (doWrite) {
            offer(dHead.qopname);
            skipSymbol();
            disjointSpan(HtmlConsts.STRING_CLASS);
            offer(postop);
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
            state = nolink ? QUOxLxN() : QUOxN();
        } else {
            state = nolink ? QUOxL() : QUO();
        }
        maybeIntraState();
        yypush(state);
    }

    /**
     * Sets a special {@code endqchar} if appropriate for {@code opener} or
     * just tracks {@code opener} as {@code endqchar}.
     */
    private void setEndQuoteChar(char opener) {
        switch (opener) {
            case '[':
                dHead.nestqchar = opener;
                dHead.endqchar = ']';
                break;
            case '<':
                dHead.nestqchar = opener;
                dHead.endqchar = '>';
                break;
            case '(':
                dHead.nestqchar = opener;
                dHead.endqchar = ')';
                break;
            case '{':
                dHead.nestqchar = opener;
                dHead.endqchar = '}';
                break;
            default:
                dHead.nestqchar = '\0';
                dHead.endqchar = opener;
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
        offer(lede);
        takeWhitespace(intervening);
        disjointSpan(HtmlConsts.STRING_CLASS);
        offer("/");
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
        offerSymbol(lede, 0, false);
        takeWhitespace(intervening);
        disjointSpan(HtmlConsts.STRING_CLASS);
        offer("/");
    }

    /**
     * Write {@code whsp} to output -- if it does not contain any LFs then the
     * full String is written; otherwise, pre-LF spaces are condensed as usual.
     */
    private void takeWhitespace(String whsp) throws IOException {
        int i;
        if ((i = whsp.indexOf("\n")) == -1) {
            offer(whsp);
        } else {
            int numlf = 1, off = i + 1;
            while ((i = whsp.indexOf("\n", off)) != -1) {
                ++numlf;
                off = i + 1;
            }
            while (numlf-- > 0) {
                startNewLine();
            }
            if (off < whsp.length()) {
                offer(whsp.substring(off));
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

        offer(capture);
        if (dHead.hereSettings == null) {
            dHead.hereSettings = new LinkedList<>();
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
            state = indented ? HEREinxN() : HERExN();
        } else {
            state = indented ? HEREin() : HERE();
        }
        settings = new HereDocSettings(terminator, state);
        dHead.hereSettings.add(settings);
    }

    /**
     * Pushes the first Here-document state if any declarations were parsed, or
     * else does nothing.
     * @return true if a Here state was pushed
     */
    public boolean maybeStartHere() throws IOException {
        if (dHead.hereSettings != null && dHead.hereSettings.size() > 0) {
            HereDocSettings settings = dHead.hereSettings.peek();
            yypush(settings.state);
            disjointSpan(HtmlConsts.STRING_CLASS);
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
        HereDocSettings settings = dHead.hereSettings.peek();
        assert settings != null;

        boolean didZspan = false;
        if (trimmed.equals(settings.terminator)) {
            disjointSpan(null);
            didZspan = true;
            dHead.hereSettings.remove();
        }

        offer(capture);

        if (dHead.hereSettings.size() > 0) {
            settings = dHead.hereSettings.peek();
            yybegin(settings.state);
            if (didZspan) {
                disjointSpan(HtmlConsts.STRING_CLASS);
            }
            return false;
        } else {
            yypop();
            return true;
        }
    }

    /**
     * Resets the interpolation counter to 1.
     */
    public void interpop() {
        dHead.nendbrace = 1;
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
        if (dHead.nendbrace <= 0) {
            return false;
        }
        if (capture.startsWith("}")) {
            if (--dHead.nendbrace <= 0) {
                int rem = capture.length() - 1;
                String opener = capture.substring(0, 1);
                popData();
                yypop();
                disjointSpan(HtmlConsts.STRING_CLASS);
                offer(opener);
                if (rem > 0) {
                    yypushback(rem);
                }
                return true;
            }
        } else if (capture.startsWith("{")) {
            ++dHead.nendbrace;
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
            offerSymbol(module, o, false);
            offer(SEP);
            o = i + 2;
        }
        if (o < capture.length()) {
            String module = capture.substring(o);
            offerSymbol(module, o, false);
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
        if (dHead.endqchar == '\0') {
            return null;
        }
        if (dHead.collateralCapture != null) {
            return dHead.collateralCapture;
        }

        StringBuilder patb = new StringBuilder("[");
        patb.append(Pattern.quote(String.valueOf(dHead.endqchar)));
        if (dHead.nestqchar != '\0') {
            patb.append(Pattern.quote(String.valueOf(dHead.nestqchar)));
        }
        patb.append("]");
        patb.append(RegexUtils.getNotFollowingEscapePattern());
        dHead.collateralCapture = Pattern.compile(patb.toString());
        return dHead.collateralCapture;
    }

    /**
     * Calls {@link #phLOC()} if the yystate is not SCOMMENT or POD.
     */
    public void chkLOC() {
        int yystate = yystate();
        if (yystate != SCOMMENT() && yystate != POD()) {
            phLOC();
        }
    }

    /**
     * Subclasses must override to possibly set the INTRA state.
     */
    abstract void maybeIntraState();

    void pushData() {
        if (data == null) {
            data = new Stack<>();
        }
        data.push(dHead);
        dHead = new RubyLexerData();
    }

    void popData() {
        dHead = data.pop();
    }

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent QUOxLxN.
     */
    abstract int QUOxLxN();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent QUOxN.
     */
    abstract int QUOxN();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent QUOxL.
     */
    abstract int QUOxL();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent QUO.
     */
    abstract int QUO();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent HEREinxN.
     */
    abstract int HEREinxN();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent HERExN.
     */
    abstract int HERExN();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent HEREin.
     */
    abstract int HEREin();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent HERE.
     */
    abstract int HERE();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent SCOMMENT.
     */
    abstract int SCOMMENT();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent POD.
     */
    abstract int POD();

    private static class HereDocSettings {
        private final String terminator;
        private final int state;

        HereDocSettings(String terminator, int state) {
            this.terminator = terminator;
            this.state = state;
        }
    }

    private static class RubyLexerData {
        private Queue<HereDocSettings> hereSettings;

        /**
         * When matching a quoting construct like qq[], q(), m//, s```, etc.,
         * the operator name (e.g., "m" or "tr") is stored. Unlike
         * {@code endqchar} it is not unset when the quote ends, because it is
         * useful to indicate if quote modifier characters are expected.
         */
        private String qopname;

        /**
         * When matching a quoting construct like %w(), '', %[], etc., the
         * terminating character is stored.
         */
        private char endqchar;

        /**
         * When matching a quoting construct like %[], %w(), %&lt;&gt; etc.
         * that nest, the begin character ('[', '&lt;', '(', or '{') is stored
         * so that nesting is tracked and {@code nendqchar} is incremented
         * appropriately.  Otherwise, {@code nestqchar} is set to '\0' if no
         * nesting occurs.
         */
        private char nestqchar;

        /**
         * When matching a quoting construct like %[], %w(), etc., the number
         * of remaining end separators is stored. It starts at 1, and any
         * nesting increases the value.
         */
        private int nendqchar;

        /**
         * When interpolating inside a quoting construct, the number of
         * remaining '}' is stored. It starts at 1, and any nesting increases
         * the value.
         */
        private int nendbrace;

        /**
         * When matching a quoting construct, a Pattern to identify collateral
         * capture characters is stored.
         */
        private Pattern collateralCapture;
    }
}
