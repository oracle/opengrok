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
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Represents a {@link Tokenizer} subclass that listens to OpenGrok language
 * lexers to produce token streams for indexing.
 * <p>
 * Created on August 24, 2009
 * @author Lubos Kosco
 */
public class JFlexTokenizer extends Tokenizer implements SymbolMatchedListener,
    NonSymbolMatchedListener {

    /**
     * To avoid over-indexing on pathologically long non-whitespace strings,
     * limit the number of sub-strings that will be indexed (for every
     * contiguous segment of non-whitespace).
     */
    private static final int MAX_NONWHITESPACE_SUBSTRINGS = 64;

    /**
     * To allow for some discarded tokens on pathologically long non-whitespace
     * strings (e.g. if length is longer than {@link #MAX_TOKEN_CHARS}), limit
     * the attempts to produce sub-strings (for every contiguous segment of
     * non-whitespace) as some number higher than
     * {@link #MAX_NONWHITESPACE_SUBSTRINGS}.
     */
    private static final int MAX_NONWHITESPACE_SUBSTRING_TRIES =
        MAX_NONWHITESPACE_SUBSTRINGS + 10;

    /**
     * Defines a limit of token string size to avoid indexing pathologically
     * long captures. (This is number of characters, not UTF-8 bytes, so a
     * power-of-2 has no benefit).
     */
    private static final int MAX_TOKEN_CHARS = 1000;

    /**
     * Matches a sub-string that starts at: 1) a word character following a word
     * boundary; or 2) that starts at a character _c_ that is not a quote,
     * nor apostrophe, nor Unicode "Punctuation, Close" where _c_ follows a
     * quote or apostrophe; or 3) that starts at a character _d_ following a
     * "Punctuation, Open" character where _d_ is not "Punctuation, Open" nor
     * "Punctuation, Close" -- and includes all remaining characters:
     * <pre>
     * {@code
     * (?Ux) (?:\b\w |  #1
     *     [^"'\p{gc=Pe}](?<=["'].) |  #2
     *     [^\p{gc=Ps}\p{gc=Pe}](?<=\p{gc=Ps}. )  #3
     *     ).*
     * }
     * </pre>
     * (Edit above and paste below [in NetBeans] for easy String escaping.)
     */
    private static final Pattern WORDPLUS = Pattern.compile(
        "(?Ux) (?:\\b\\w |" +
        "    [^\"'\\p{gc=Pe}](?<=[\"'].) |" +
        "    [^\\p{gc=Ps}\\p{gc=Pe}](?<=\\p{gc=Ps}.)" +
        "    ).*");

    /**
     * Matches: 1) a word boundary following a word character or a
     * non-word/non-full-stop character; or 2) a quote or apostrophe following a
     * non-quote or -apostrophe; or 3) a non-"Punctuation, Close" following a
     * "Punctuation, Close"; or 4) a Unicode "Punctuation, Close" character
     * following another character (the "following another character" is
     * implicit because {@link #PAST_PHRASE} is only matched for offset
     * &gt;= 1).
     * <pre>
     * {@code
     * (?Ux) \b(?<=\w|[^\w\.]) |  #1
     *     ["'](?<=[^"'].) |  #2
     *     \P{gc=Pe}(?<=\p{gc=Pe}.) |  #3
     *     \p{gc=Pe}  #4
     * }
     * </pre>
     * (Edit above and paste below [in NetBeans] for easy String escaping.)
     */
    private static final Pattern PAST_PHRASE = Pattern.compile(
        "(?Ux) \\b(?<=\\w|[^\\w\\.]) |" +
        "    [\"'](?<=[^\"'].) |" +
        "    \\P{gc=Pe}(?<=\\p{gc=Pe}.) |" +
        "    \\p{gc=Pe}");

    /**
     * Matches a string that is all non-word characters.
     * <pre>
     * {@code
     * (?U)^\W*$
     * }
     * </pre>
     * (Edit above and paste below [in NetBeans] for easy String escaping.)
     */
    private static final Pattern ALL_NONWORD = Pattern.compile("(?U)^\\W*$");

    private final ScanningSymbolMatcher matcher;

    private final CharTermAttribute termAtt = addAttribute(
        CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(
        OffsetAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(
        PositionIncrementAttribute.class);

    /**
     * Defines the ultimate queue of tokens to be produced by
     * {@link #incrementToken()}.
     */
    private final Queue<PendingToken> events = new LinkedList<>();

    /**
     * Tracks unique pending tokens in {@link #events} (which is not
     * necessarily unique across the document but close enough for this
     * class's purposes).
     */
    private final Set<PendingToken> eventsSet = new HashSet<>();

    /**
     * When {@link TokenizerMode} allows overlapping tokens, in order to avoid a
     * Lucene {@link IllegalArgumentException} related to mis-ordered offsets
     * ("... offsets must not go backwards"), tokens are accumulated in the
     * following list until an indication that local overlapping is detected.
     * Then the following list will be sorted and its tokens queued to
     * {@link events}.
     */
    private final List<PendingToken> eventHopper = new ArrayList<>();

    /**
     * Tracks unique symbol tokens -- until the next {@link #reset()}.
     */
    private final Set<PendingToken> symbolsSet = new HashSet<>();

    /**
     * Tracks a transient list of sub-strings of a string, where the sub-strings
     * start at different left positions and use the entire rest of the
     * original string. E.g., {@code "func(a,b)"} ->
     * {@code ["func(a,b)", "a,b)", "b)"]}.
     */
    private final List<PendingSub> lsubs = new ArrayList<>();

    private final StringBuilder nonWhitespaceBuilder = new StringBuilder();

    private int nonWhitespaceOff = -1;

    private TokenizerMode mode = TokenizerMode.SYMBOLS_ONLY;

    private Supplier<TokenizerMode> modeGetter;

    private PendingToken lastPublished;

    /**
     * Initialize an instance, passing a {@link ScanningSymbolMatcher} which
     * will be owned by the {@link JFlexTokenizer}.
     * @param matcher a defined instance
     */
    public JFlexTokenizer(ScanningSymbolMatcher matcher) {
        if (matcher == null) {
            throw new IllegalArgumentException("`matcher' is null");
        }
        this.matcher = matcher;
        matcher.setSymbolMatchedListener(this);
        matcher.setNonSymbolMatchedListener(this);
        // The tokenizer will own the matcher, so we won't have to unsubscribe.
    }

    /**
     * Gets a value indicating how the tokenizer tracks symbols and -- if the
     * {@link ScanningSymbolMatcher} supports it -- contiguous, non-whitespace
     * sub-strings. Default is {@link TokenizerMode#SYMBOLS_ONLY}.
     */
    public TokenizerMode getTokenizerMode() {
        return mode;
    }

    /**
     * Sets a value indicating how the tokenizer tracks symbols and -- if the
     * {@link ScanningSymbolMatcher} supports it -- contiguous, non-whitespace
     * sub-strings.
     */
    public void setTokenizerMode(TokenizerMode value) {
        if (value != this.mode) {
            this.mode = value;
            if (value == TokenizerMode.SYMBOLS_ONLY) {
                nonWhitespaceBuilder.setLength(0);
                nonWhitespaceOff = -1;
            }
            matcher.setTokenizerMode(value);
        }
    }

    /**
     * Sets an object for deferring the setting of
     * {@link #setTokenizerMode(org.opensolaris.opengrok.analysis.TokenizerMode)}
     * during {@link #reset()} -- e.g. when the {@link JFlexTokenizer} is
     * passed off to a higher-level Lucene object.
     * @param getter a defined instance or {@code null} to set
     * {@link #getTokenizerMode()} to its default value
     */
    public void setTokenizerModeSupplier(Supplier<TokenizerMode> getter) {
        this.modeGetter = getter;
        if (getter != null) {
            setTokenizerMode(getter.get());
        } else {
            setTokenizerMode(TokenizerMode.SYMBOLS_ONLY);
        }
    }

    /**
     * Resets the instance and the instance's {@link ScanningSymbolMatcher}.
     * <p>
     * N.b. {@link #getTokenizerMode()} is not affected unless
     * {@link #setTokenizerModeSupplier(java.util.function.Supplier)} was called
     * with a defined instance.
     * <p>
     * If necessary, users should have first called this instance's
     * {@link #setReader(java.io.Reader)} since the matcher will be
     * reset to the current reader.
     * @throws java.io.IOException in case of I/O error
     */
    @Override
    public void reset() throws IOException {
        super.reset();

        clearAttributes();
        eventHopper.clear();
        events.clear();
        eventsSet.clear();
        lastPublished = null;
        // `lsubs' is managed exclusively by addNonWhitespaceSubstrings().
        // `mode' is (possibly) managed below.
        nonWhitespaceBuilder.setLength(0);
        nonWhitespaceOff = -1;
        symbolsSet.clear();

        Supplier<TokenizerMode> getter = modeGetter;
        if (getter != null) {
            setTokenizerMode(getter.get());
        }

        matcher.yyreset(input);
        matcher.reset();
    }

    /**
     * Closes the instance and the instance's {@link ScanningSymbolMatcher}.
     * @throws IOException if any error occurs while closing
     */
    @Override
    public final void close() throws IOException {
        super.close();
        matcher.yyclose();
    }

    /**
     * Executes {@link ScanningSymbolMatcher#yylex()} until either a token is
     * produced or the EOF is reached; and calls
     * {@link #setAttribs(org.opensolaris.opengrok.analysis.JFlexTokenizer.PendingToken)}
     * upon the former.
     * @return false if no more tokens, otherwise true
     * @throws IOException in case of I/O error
     */
    @Override
    public final boolean incrementToken() throws IOException {
        while (events.isEmpty() && notEOF()) {
            // just iterating
        }

        if (!events.isEmpty()) {
            PendingToken tok = events.remove();
            eventsSet.remove(tok);
            setAttribs(tok);
            lastPublished = tok;
            return true;
        }

        clearAttributes();
        lastPublished = null;
        return false;
    }

    /**
     * Enqueues a token on the publishing of a {@link SymbolMatchedEvent}, and
     * does additional non-whitespace sub-string handling if
     * {@link #getTokenizerMode()} is eligible.
     * @param evt the event raised
     */
    @Override
    public void symbolMatched(SymbolMatchedEvent evt) {
        switch (mode) {
            case SYMBOLS_ONLY:
            case SYMBOLS_AND_NON_WHITESPACE:
                PendingToken tok = new PendingToken(evt.getStr(),
                    evt.getStart(), evt.getEnd());
                if (addEventToken(tok)) {
                    symbolsSet.add(tok);
                }
                break;
            default:
                break;
        }

        onTextMatched(evt.getSource(), evt.getStr(), evt.getStart());
    }

    /**
     * Does nothing.
     * @param evt ignored
     */
    @Override
    public void sourceCodeSeen(SourceCodeSeenEvent evt) {
    }

    /**
     * Does non-whitespace sub-string handling if {@link #getTokenizerMode()}
     * is eligible.
     * @param evt the event raised
     */
    @Override
    public void nonSymbolMatched(TextMatchedEvent evt) {
        onTextMatched(evt.getSource(), evt.getStr(), evt.getStart());
    }

    /**
     * Does non-whitespace sub-string handling if {@link #getTokenizerMode()}
     * is eligible.
     * @param evt the event raised
     */
    @Override
    public void keywordMatched(TextMatchedEvent evt) {
        onTextMatched(evt.getSource(), evt.getStr(), evt.getStart());
    }

    /**
     * Does non-whitespace sub-string handling if {@link #getTokenizerMode()}
     * is eligible.
     * @param evt the event raised
     */
    @Override
    public void endOfLineMatched(TextMatchedEvent evt) {
        onTextMatched(evt.getSource(), evt.getStr(), evt.getStart());
    }

    /**
     * Does nothing.
     * @param evt ignored
     */
    @Override
    public void disjointSpanChanged(DisjointSpanChangedEvent evt) {
    }

    /**
     * Does non-whitespace sub-string handling if {@link #getTokenizerMode()}
     * is eligible.
     * @param evt the event raised
     */
    @Override
    public void linkageMatched(LinkageMatchedEvent evt) {
        onTextMatched(evt.getSource(), evt.getStr(), evt.getStart());
    }

    /**
     * Does non-whitespace sub-string handling if {@link #getTokenizerMode()}
     * is eligible.
     * @param evt the event raised
     */
    @Override
    public void pathlikeMatched(PathlikeMatchedEvent evt) {
        onTextMatched(evt.getSource(), evt.getStr(), evt.getStart());
    }

    /**
     * Does non-whitespace sub-string handling if {@link #getTokenizerMode()}
     * is eligible.
     * @param evt the event raised
     */
    @Override
    public void scopeChanged(ScopeChangedEvent evt) {
        onTextMatched(evt.getSource(), evt.getStr(), evt.getStart());
    }

    /**
     * Clears, and then resets the instance's attributes per the specified
     * argument.
     * @param tok the matched token
     */
    protected void setAttribs(PendingToken tok) {
        clearAttributes();

        this.posIncrAtt.setPositionIncrement(tok.nonpos ? 0 : 1);
        this.termAtt.setEmpty();
        this.termAtt.append(tok.str);
        this.offsetAtt.setOffset(tok.start, tok.end);
    }

    /**
     * If {@link #getTokenizerMode()} is eligible, then does handling w.r.t.
     * {@link #addNonWhitespace()}.
     */
    private void onTextMatched(Object source, String str, int start) {
        switch (mode) {
            case NON_WHITESPACE_ONLY:
            case SYMBOLS_AND_NON_WHITESPACE:
                for (int i = 0; i < str.length(); ++i) {
                    char c = str.charAt(i);
                    if (Character.isWhitespace(c)) {
                        if (nonWhitespaceOff >= 0) {
                            addNonWhitespace();
                        }
                        /**
                         * In OpenGrok, a symbol will never begin with a
                         * whitespace character, so whenever this method sees a
                         * text whitespace, that means any possible local
                         * overlapping of SYMBOLS_AND_NON_WHITESPACE is over.
                         * eventHopper is sorted and its contents published to
                         * events.
                         */
                        emptyHopperToQueue();
                    } else if (nonWhitespaceOff < 0) {
                        nonWhitespaceOff = start + i;
                        nonWhitespaceBuilder.append(c);
                    } else {
                        nonWhitespaceBuilder.append(c);
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * Executes the {@link ScanningSymbolMatcher#yylex()}, and tests whether it
     * returned {@link ScanningSymbolMatcher#getYYEOF()} -- if so, then
     * any necessary, finishing operations are executed.
     * @return {@code true} if {@code YYEOF} was not returned
     */
    private boolean notEOF() throws IOException {
        boolean isEOF = matcher.yylex() == matcher.getYYEOF();
        if (isEOF) {
            if (nonWhitespaceOff >= 0) {
                addNonWhitespace();
            }
            emptyHopperToQueue();
        }
        return !isEOF;
    }

    /**
     * If non-whitespace has been collected according to
     * {@link #getTokenizerMode()}, then queue at least one
     * {@link PendingToken} and possibly more according to
     * {@link #getTokenizerMode()}.
     */
    private void addNonWhitespace() {
        if (nonWhitespaceBuilder.length() > 0) {
            String nonwhsp = nonWhitespaceBuilder.toString();
            nonWhitespaceBuilder.setLength(0);
            PendingToken tok = new PendingToken(nonwhsp, nonWhitespaceOff,
                nonWhitespaceOff + nonwhsp.length());
            addEventToken(tok);

            /*
             * In the most expansive mode, additional sub-strings within
             * non-whitespace matches are also tokenized.
             */
            if (mode == TokenizerMode.SYMBOLS_AND_NON_WHITESPACE) {
                addNonWhitespaceSubstrings(nonwhsp);
            }
        }
        nonWhitespaceOff = -1;
    }

    /**
     * Queues additional, word-boundary sub-string tokens found within
     * {@code fullsub}.
     */
    private void addNonWhitespaceSubstrings(String fullsub) {
        if (fullsub.length() < 1) {
            return;
        }

        lsubs.clear();
        /*
         * Track a (not-published-here) entry for `fullsub' to be used for later
         * iterations.
         */
        lsubs.add(new PendingSub(fullsub, nonWhitespaceOff));

        int successes = 0;
        int tries = 0;

        /*
         * Add any sub-strings of `fullsub' starting at word(plus)-boundaries
         * except for a sub-string that is entirely `fullsub'. Avoid splitting
         * any known language contractions by tracking `xlen0'.
         */
        int xlen0 = matcher.getLongestContractionPrefix(fullsub);
        int moff = 0;
        Matcher lsubMatcher = WORDPLUS.matcher(fullsub);
        while (lsubMatcher.find(moff)) {
            String lsub = lsubMatcher.group();
            int loff = nonWhitespaceOff + lsubMatcher.start();
            if (loff > nonWhitespaceOff && loff >= nonWhitespaceOff + xlen0) {
                // Extend the contraction-protection region if necessary.
                xlen0 = lsubMatcher.start() +
                    matcher.getLongestContractionPrefix(lsub);

                PendingToken tok = new PendingToken(lsub, loff, loff +
                    lsub.length());
                lsubs.add(new PendingSub(lsub, loff));
                if (addEventToken(tok) && ++successes >=
                        MAX_NONWHITESPACE_SUBSTRINGS) {
                    return;
                }
                if (++tries >= MAX_NONWHITESPACE_SUBSTRING_TRIES) {
                    return;
                }
            }
            moff = lsubMatcher.start() + 1;
        }

        // Initialize PAST_PHRASE matchers in lsubs.
        for (PendingSub psub : lsubs) {
            psub.ender = PAST_PHRASE.matcher(psub.str);
            psub.roff = 1; // Start looking past the 0th character.
            psub.xlen = matcher.getLongestContractionPrefix(psub.str);
        }

        /*
         * Add any sub-strings of PendingSub-strings ending at phrase
         * boundaries beyond the 0th character, short of the full length of the
         * PendingSub-string, and beyond any known language contractions per
         * `psub.xlen'.
         *
         * Because the number of sub-strings produced by this method is limited
         * to MAX_NONWHITESPACE_SUBSTRINGS, iterate in a circular fashion
         * through `lsubs' so that sub-strings (if limited) are spread evenly
         * among `lsubs'. I.e., so that sub-strings aren't exhausted from the
         * first entry of `lsubs' while the last one gets none.
         */
        int pidx = -1;
        while ((pidx = circlePendingSubs(pidx)) >= 0) {
            PendingSub psub = lsubs.get(pidx);
            boolean didAddToken = false;
            while (psub.ender.find(psub.roff) && psub.ender.start() <
                    psub.str.length()) {
                int pends = psub.ender.start();
                psub.roff = pends + 1;
                if (pends >= psub.xlen) {
                    String lrsub = psub.str.substring(0, pends);
                    if (!isLoneNonWordlikeChar(lrsub)) {
                        PendingToken tok = new PendingToken(lrsub, psub.start,
                            psub.start + lrsub.length());
                        if (addEventToken(tok)) {
                            if (++successes >= MAX_NONWHITESPACE_SUBSTRINGS) {
                                return;
                            }
                            // After one added token, break to circle in lsubs.
                            didAddToken = true;
                            break;
                        }
                        if (++tries >= MAX_NONWHITESPACE_SUBSTRING_TRIES) {
                            return;
                        }
                    }
                }
            }
            if (!didAddToken) {
                /**
                 * If no new token was got from `pidx', remove the entry from
                 * `lsubs' so the candidates shrink; and then move `pidx' back
                 * circularly to the previous entry.
                 */
                lsubs.remove(pidx);
                pidx = -1 + (pidx > 0 ? pidx : lsubs.size());
            }
        }
    }

    /**
     * Queues the specified {@code tok} if: 1) its size is eligible; 2)
     * {@code tok} is not equal to the last published token; and 3) {@code tok}
     * is not present in the tracked set of symbol tokens nor in the tracked set
     * of pending tokens.
     */
    private boolean addEventToken(PendingToken tok) {
        if (tok.str.length() > 0 && tok.str.length() <= MAX_TOKEN_CHARS &&
                (lastPublished == null || !lastPublished.equals(tok)) &&
                !symbolsSet.contains(tok)) {
            if (eventsSet.add(tok)) {
                switch (mode) {
                    case SYMBOLS_AND_NON_WHITESPACE:
                        /**
                         * In OpenGrok, a symbol will never begin with a
                         * whitespace character, so whenever this method sees a
                         * token starting with whitespace, that means any
                         * possible local overlapping of
                         * SYMBOLS_AND_NON_WHITESPACE is over. eventHopper is
                         * sorted and its contents published to events.
                         */
                        if (Character.isWhitespace(tok.str.charAt(0))) {
                            emptyHopperToQueue();
                            return events.add(tok);
                        } else {
                            return eventHopper.add(tok);
                        }
                    default:
                        return events.add(tok);
                }
            }
        }

        return false;
    }

    /**
     * Moves all elements from {@link #eventHopper} to {@link #events} after
     * first ordering the former and determining position increment values for
     * the ordered elements relative to their predecessors in the hopper.
     */
    private void emptyHopperToQueue() {
        if (eventHopper.size() < 1) {
            return;
        }

        if (eventHopper.size() == 1) {
            events.add(eventHopper.get(0));
            eventHopper.clear();
            return;
        }

        eventHopper.sort(PendingTokenOffsetsComparator.INSTANCE);
        int lastNewStart = -1;
        int lastNewPos = -1;
        String presentWord = "";
        String lastWord = "";
        for (PendingToken ntok : eventHopper) {
            events.add(ntok);

            // When PendingToken `start' changes, begin a new `presentWord'.
            if (ntok.start != lastNewStart) {
                lastNewStart = ntok.start;
                presentWord = ntok.str;
            } else if (ntok.str.length() > presentWord.length()) {
                // Extend `presentWord' to a longer value.
                presentWord = ntok.str;
            }

            /**
             * Track `lastNewPos' to indicate the start position of last token
             * with a non-zero position increment. After `lastNewPos' is first
             * defined, the current token may have a position increment of zero
             * or non-zero depending on the presence (or not) of only non-word
             * characters between the current token and `lastNewPos'.
             *
             * This is meant to allow phrase comparisons when minor interleaving
             * punctuation is present; e.g. so that a match can occur for
             * "contains some strange" against the source text:
             * "contains some 'strange' characters".
             */

            if (lastNewPos < 0) {
                lastNewPos = ntok.start;
                lastWord = presentWord;
            } else if (ntok.start == lastNewPos) {
                ntok.nonpos = true;
                lastWord = presentWord;
            } else {
                /**
                 * With overlapping tokens, if the sub-string preceding `ntok'
                 * is all non-word characters, then also set `nonpos' to true.
                 */
                if (lastWord.length() >= ntok.end - lastNewPos) {
                    String lastLede = lastWord.substring(0, ntok.start -
                        lastNewPos);
                    if (allNonWord(lastLede)) {
                        ntok.nonpos = true;
                    } else {
                        lastNewPos = ntok.start;
                        lastWord = presentWord;
                    }
                } else {
                    lastNewPos = ntok.start;
                    lastWord = presentWord;
                }
            }
        }
        eventHopper.clear();
    }

    /**
     * Determines if {@code pword} consists of all non-word characters (or
     * likewise is empty).
     */
    private boolean allNonWord(String pword) {
        return ALL_NONWORD.matcher(pword).matches();
    }

    /**
     * Determines if {@code str} is a single character that is not a letter,
     * digit, or underscore.
     */
    private boolean isLoneNonWordlikeChar(String str) {
        if (str.length() != 1) {
            return false;
        }
        char c = str.charAt(0);
        return c != '_' && !Character.isLetterOrDigit(c);
    }

    /**
     * Iterates through {@link #lsubs} in a circular fashion.
     */
    private int circlePendingSubs(int currentIdx) {
        int csize = lsubs.size();
        if (currentIdx == -1 || ++currentIdx >= csize) {
            return csize > 0 ? 0 : -1;
        }
        return currentIdx;
    }

    private static class PendingSub {
        public final String str;
        public final int start;
        public Matcher ender;
        public int roff;
        public int xlen;

        public PendingSub(String str, int start) {
            this.str = str;
            this.start = start;
        }
    }
}
