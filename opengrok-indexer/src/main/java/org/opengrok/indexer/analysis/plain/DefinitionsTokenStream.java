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
 * Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.PendingToken;
import org.opengrok.indexer.analysis.PendingTokenOffsetsComparator;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.util.LineBreaker;
import org.opengrok.indexer.util.ReaderWrapper;

/**
 * Represents a token stream from {@link Definitions}.
 */
public class DefinitionsTokenStream extends TokenStream {

    /**
     * Defines the ultimate queue of tokens to be produced by
     * {@link #incrementToken()}.
     */
    private final List<PendingToken> events = new ArrayList<>();

    private final CharTermAttribute termAtt = addAttribute(
        CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(
        OffsetAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(
        PositionIncrementAttribute.class);

    private int offset;

    /**
     * Initializes the stream by merging {@code defs} with cross-referenced
     * line offsets read from {@code src}.
     * @param defs a defined instance
     * @param src a defined instance
     * @param wrapper an optional instance
     * @throws IOException if I/O error occurs
     */
    public void initialize(Definitions defs, StreamSource src,
            ReaderWrapper wrapper) throws IOException {
        if (defs == null) {
            throw new IllegalArgumentException("`defs' is null");
        }
        if (src == null) {
            throw new IllegalArgumentException("`src' is null");
        }

        events.clear();
        offset = 0;

        LineBreaker brk = new LineBreaker();
        brk.reset(src, wrapper);
        createTokens(defs, brk);
    }

    /**
     * Publishes the next, pending token from
     * {@link #initialize(org.opengrok.indexer.analysis.Definitions, org.opengrok.indexer.analysis.StreamSource,
     * org.opengrok.indexer.util.ReaderWrapper)},
     * if one is available.
     * @return false if no more tokens; otherwise true
     * @throws IOException in case of I/O error
     */
    @Override
    public final boolean incrementToken() throws IOException {
        if (offset < events.size()) {
            PendingToken tok = events.get(offset++);
            setAttribs(tok);
            return true;
        }

        clearAttributes();
        return false;
    }

    private void setAttribs(PendingToken tok) {
        clearAttributes();

        this.posIncrAtt.setPositionIncrement(tok.nonpos ? 0 : 1);
        this.termAtt.setEmpty();
        this.termAtt.append(tok.str);
        this.offsetAtt.setOffset(tok.start, tok.end);
    }

    private void createTokens(Definitions defs, LineBreaker brk) {
        for (Definitions.Tag tag : defs.getTags()) {
            // Shift from ctags's convention.
            int lineno = tag.line - 1;

            if (lineno >= 0 && lineno < brk.count() && tag.symbol != null &&
                    tag.text != null) {
                int lineoff = brk.getOffset(lineno);
                if (tag.lineStart >= 0) {
                    PendingToken tok = new PendingToken(tag.symbol, lineoff +
                        tag.lineStart, lineoff + tag.lineEnd);
                    events.add(tok);
                }
            }
        }

        events.sort(PendingTokenOffsetsComparator.INSTANCE);
    }
}
