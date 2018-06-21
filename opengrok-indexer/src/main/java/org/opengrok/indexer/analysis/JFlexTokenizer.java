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
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Created on August 24, 2009
 *
 * @author Lubos Kosco
 */
public class JFlexTokenizer extends Tokenizer
    implements SymbolMatchedListener {

    private final ScanningSymbolMatcher matcher;

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
        clearAttributes();
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
     * This will re-initialize internal AttributeImpls, or it returns false if
     * end of input Reader ...
     *
     * @return false if no more tokens, otherwise true
     * @throws IOException in case of I/O error
     */
    @Override
    public final boolean incrementToken() throws IOException {
        clearAttributes();
        return matcher.yylex() != matcher.getYYEOF();
    }

    /**
     * Calls {@link #setAttribs(java.lang.String, int, int)} on the publishing
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
     * arguments.
     * @param str the matched symbol
     * @param start the match start position
     * @param end the match end position
     */
    protected void setAttribs(String str, int start, int end) {
        clearAttributes();
        //FIXME increasing below by one(default) might be tricky, need more analysis
        // after lucene upgrade to 3.5 below is most probably not even needed        
        this.posIncrAtt.setPositionIncrement(1);
        this.termAtt.setEmpty();
        this.termAtt.append(str);
        this.offsetAtt.setOffset(start, end);
    }
}
