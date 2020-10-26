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
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.AttributeImpl;

/**
 * Tokenizer which uses lex to identify tokens.
 *
 * Created on August 24, 2009
 * @author Lubos Kosco
 */
public class JFlexTokenizer extends Tokenizer
    implements SymbolMatchedListener {

    private static final int LUCENE_MAX_TOKEN_LENGTH = 32766;

    private final ScanningSymbolMatcher matcher;
    private boolean didSetAttribsValues;

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
        // The tokenizer will own the matcher, so we won't have to unsubscribe.
    }

    /**
     * Resets the instance and the instance's {@link ScanningSymbolMatcher}.
     * If necessary, users should have first called this instance's
     * {@link #setReader(java.io.Reader)} since the matcher will be
     * reset to the current reader.
     * @throws java.io.IOException in case of I/O error
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        matcher.yyreset(input);
        matcher.reset();
        clearAttributesEtc();
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

    private final CharTermAttribute termAtt = addAttribute(
        CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(
        OffsetAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(
        PositionIncrementAttribute.class);

    /**
     * Attempts to advance the stream to the next acceptable token, and updates
     * the appropriate {@link AttributeImpl}s.
     *
     * @return {@code true} if an acceptable token was produced; {@code false}
     * otherwise to indicate end of stream
     * @throws IOException in case of I/O error
     */
    @Override
    public final boolean incrementToken() throws IOException {
        boolean notAtEOF;
        do {
            clearAttributesEtc();
            notAtEOF = matcher.yylex() != matcher.getYYEOF();
        } while (!didSetAttribsValues && notAtEOF);
        return notAtEOF;
    }

    /**
     * Calls {@link #setAttribs(String, long, long)} on the publishing
     * of a {@link SymbolMatchedEvent}.
     * @param evt the event raised
     */
    @Override
    public void symbolMatched(SymbolMatchedEvent evt) {
        setAttribs(evt.getStr(), evt.getStart(), evt.getEnd());
    }

    /**
     * Does nothing.
     * @param evt ignored
     */
    @Override
    public void sourceCodeSeen(SourceCodeSeenEvent evt) {
    }

    /**
     * Clears, and then resets the instances attributes per the specified
     * arguments. If {@code start} or {@code end} is past
     * {@link Integer#MAX_VALUE}, then only the clearing occurs.
     * @param str the matched symbol
     * @param start the match start position
     * @param end the match end position
     */
    protected void setAttribs(String str, long start, long end) {
        clearAttributesEtc();
        if (start < Integer.MAX_VALUE && end < Integer.MAX_VALUE) {

            if (str.length() > LUCENE_MAX_TOKEN_LENGTH) {
                str = str.substring(0, LUCENE_MAX_TOKEN_LENGTH);
                /*
                 * Leave `end` unadjusted. The truncated string will represent
                 * the full source text, similar to how a Lucene synonym is an
                 * alternative representation of full source text.
                 */
            }

            /*
             * FIXME increasing below by one(default) might be tricky, need more
             * analysis after lucene upgrade to 3.5 below is most probably not
             * even needed.
             */
            this.posIncrAtt.setPositionIncrement(1);
            this.termAtt.setEmpty();
            this.termAtt.append(str);
            this.offsetAtt.setOffset((int) start, (int) end);
            this.didSetAttribsValues = true;
        }
    }

    /**
     * Calls {@link #clearAttributes()}, and also resets some additional tracked
     * state.
     */
    protected void clearAttributesEtc() {
        clearAttributes();
        didSetAttribsValues = false;
    }
}
