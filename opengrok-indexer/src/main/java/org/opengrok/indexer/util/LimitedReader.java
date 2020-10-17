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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import java.io.IOException;
import java.io.Reader;

/**
 * Represents a {@link Reader} wrapper that limits characters read as specified
 * and only up to {@link Integer#MAX_VALUE} to accommodate Lucene offset limits.
 */
public class LimitedReader extends Reader {

    private final int characterLimit;
    private final Reader underlying;
    private int characterCount;
    private boolean didEOF;

    /**
     * Initializes a new instance to wrap the specified {@code underlying}.
     * @param underlying a defined instance
     * @param characterLimit a non-negative number or alternatively a negative
     *                       number to indicate {@link Integer#MAX_VALUE}
     */
    public LimitedReader(Reader underlying, int characterLimit) {
        if (underlying == null) {
            throw new IllegalArgumentException("underlying is null");
        }
        this.underlying = underlying;
        this.characterLimit = characterLimit < 0 ? Integer.MAX_VALUE : characterLimit;
    }

    /**
     * Calls {@link Reader#read()} on the underlying {@link Reader} but only
     * up to {@code characterLimit}, after which EOF will be indicated.
     * @return The number of characters read, or -1 if the end of the stream or
     * the {@code characterLimit} has been reached
     */
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (didEOF) {
            return -1;
        }

        int adjustedLen = Math.min(len, characterLimit - characterCount);
        int ret = underlying.read(cbuf, off, adjustedLen);
        if (ret < 0) {
            didEOF = true;
            return -1;
        }
        characterCount += ret;
        if (characterCount >= characterLimit) {
            didEOF = true;
        }
        return ret;
    }

    /**
     * Calls {@link Reader#close()} on the underlying {@link Reader}.
     */
    @Override
    public void close() throws IOException {
        underlying.close();
    }
}
