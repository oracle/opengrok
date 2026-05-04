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
 * Copyright (c) 2026, Siyabend Urun <urunsiyabend@gmail.com>.
 */
package org.opengrok.indexer.analysis.cobol;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Free-format counterpart to {@link HyphenAwareCobolFixedSymbolTokenizer}.
 * See that class for the full rationale.
 */
final class HyphenAwareCobolFreeSymbolTokenizer extends CobolFreeSymbolTokenizer {

    private record Piece(String str, long start) { }

    private final Deque<Piece> queue = new ArrayDeque<>();

    HyphenAwareCobolFreeSymbolTokenizer(Reader in) {
        super(in);
    }

    @Override
    public int yylex() throws IOException {
        if (!queue.isEmpty()) {
            Piece p = queue.poll();
            onSymbolMatched(p.str(), p.start());
            return yystate();
        }
        return super.yylex();
    }

    @Override
    public boolean offerSymbol(String value, int captureOffset, boolean ignoreKwd)
            throws IOException {
        long base = getYYCHAR() + captureOffset;
        boolean result = super.offerSymbol(value, captureOffset, ignoreKwd);
        if (result && value.indexOf('-') >= 0) {
            int off = 0;
            for (String piece : value.split("-")) {
                if (!piece.isEmpty()) {
                    queue.add(new Piece(piece, base + off));
                }
                off += piece.length() + 1;
            }
        }
        return result;
    }

    @Override
    public void reset() {
        super.reset();
        queue.clear();
    }
}
