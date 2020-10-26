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
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.hcl;

import org.opengrok.indexer.analysis.JFlexJointLexer;
import org.opengrok.indexer.analysis.JFlexSymbolMatcher;
import org.opengrok.indexer.analysis.Resettable;
import org.opengrok.indexer.web.HtmlConsts;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents an abstract base class for HCL lexers.
 */
@SuppressWarnings("DuplicatedCode")
public abstract class HCLLexer extends JFlexSymbolMatcher
        implements JFlexJointLexer, Resettable {

    // Defined to be the equivalent of {Identifier} from HCL.lexh
    private static final Pattern HERE_TERMINATOR_MATCH = Pattern.compile(
        "^\\p{javaUnicodeIdentifierStart}(\\p{javaUnicodeIdentifierPart}|-)*");

    private HCLLexerData dataHead;

    private Stack<HCLLexerData> data;

    public HCLLexer() {
        dataHead = new HCLLexerData();
    }

    /**
     * Resets the instance to an initial state.
     */
    @Override
    public void reset() {
        super.reset();
        dataHead = new HCLLexerData();
        if (data != null) {
            data.clear();
        }
    }

    /**
     * Parses a Here-document declaration, and takes the {@code capture} using
     * {@link HCLLexer#offer(String)}. If the declaration is valid,
     * {@code hereSettings} will have been appended.
     */
    public void hereOp(String capture) throws IOException {
        if (!capture.startsWith("<<")) {
            throw new IllegalArgumentException("bad HERE: " + capture);
        }

        offer(capture);
        if (dataHead.hereSettings == null) {
            dataHead.hereSettings = new LinkedList<>();
        }

        String remaining = capture;
        boolean indented = false;
        int i = 0;
        HereDocSettings settings;
        String terminator;

        String opener = remaining.substring(0, i + "<<".length());
        remaining = remaining.substring(opener.length());
        if (remaining.startsWith("-")) {
            indented = true;
            remaining = remaining.substring(1);
        }

        // Trim any whitespace, which is allowed by HCL after the HERE op.
        remaining = remaining.replaceFirst("^\\s+", "");

        Matcher m = HERE_TERMINATOR_MATCH.matcher(remaining);
        if (!m.find()) {
            return;
        }
        terminator = m.group(0);

        int state = indented ? HEREin() : HERE();
        settings = new HereDocSettings(terminator, state);
        dataHead.hereSettings.add(settings);
    }

    /**
     * Pushes the first Here-document state if any declarations were parsed, or
     * else does nothing.
     * @return true if a Here state was pushed
     */
    public boolean maybeHereStart() throws IOException {
        if (dataHead.hereSettings != null && dataHead.hereSettings.size() > 0) {
            HereDocSettings settings = dataHead.hereSettings.peek();
            yypush(settings.state);
            disjointSpan(HtmlConsts.STRING_CLASS);
            return true;
        }
        return false;
    }

    /**
     * Process the {@code capture}, possibly ending the Here-document state
     * just beforehand.
     * @return true if the Here state ended
     */
    public boolean maybeHereEnd(String capture) throws IOException {
        String trimmed = capture.replaceFirst("^\\s+", "");
        HereDocSettings settings = dataHead.hereSettings.peek();
        assert settings != null;

        boolean didZspan = false;
        if (trimmed.equals(settings.terminator)) {
            disjointSpan(null);
            didZspan = true;
            dataHead.hereSettings.remove();
        }

        offer(capture);

        if (dataHead.hereSettings.size() > 0) {
            settings = dataHead.hereSettings.peek();
            yybegin(settings.state);
            if (didZspan) {
                disjointSpan(HtmlConsts.STRING_CLASS);
            }
            return false;
        }
        yypop();
        return true;
    }

    /**
     * Resets the interpolation counter to 1.
     */
    public void interpOp() {
        dataHead.numEndBrace = 1;
    }

    /**
     * Determine if the interpolation should end based on the first character
     * of {@code capture}, recognizing tokens that increase the nesting level
     * instead.
     * <p>
     * Calling this method has side effects to possibly modify
     * {@code numEndBrace}.
     * @return true if the interpolation state ended
     */
    public boolean maybeInterpolationEnd(String capture) throws IOException {
        if (dataHead.numEndBrace <= 0) {
            return false;
        }
        if (capture.startsWith("}")) {
            if (--dataHead.numEndBrace <= 0) {
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
            ++dataHead.numEndBrace;
        }
        return false;
    }

    /**
     * Calls {@link #phLOC()} if the yystate is not COMMENT or SCOMMENT.
     */
    public void chkLOC() {
        int yystate = yystate();
        if (yystate != COMMENT() && yystate != SCOMMENT()) {
            phLOC();
        }
    }

    /**
     * Push the lexer state data on the stack, and initialize a new state.
     */
    public void pushData() {
        if (data == null) {
            data = new Stack<>();
        }
        data.push(dataHead);
        dataHead = new HCLLexerData();
    }

    /**
     * Discard the current lexer state, and pop state off the stack.
     */
    public void popData() {
        dataHead = data.pop();
    }

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent COMMENT.
     */
    public abstract int COMMENT();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent SCOMMENT.
     */
    public abstract int SCOMMENT();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent HERE.
     */
    public abstract int HERE();

    /**
     * Subclasses must override to get the constant value created by JFlex to
     * represent HEREin.
     */
    public abstract int HEREin();

    private static class HereDocSettings {
        private final String terminator;
        private final int state;

        HereDocSettings(String terminator, int state) {
            this.terminator = terminator;
            this.state = state;
        }
    }

    private static class HCLLexerData {
        private Queue<HereDocSettings> hereSettings;

        /**
         * When interpolating inside a quoting construct, the number of
         * remaining '}' is stored. It starts at 1, and any nesting increases
         * the value.
         */
        private int numEndBrace;
    }
}
