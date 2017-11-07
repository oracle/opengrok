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

package org.opensolaris.opengrok.analysis.ada;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.Resettable;

/**
 * Represents an API for object's using {@link AdaLexHelper}
 */
interface AdaLexListener {
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
 * Represents a helper for Ada lexers
 */
class AdaLexHelper implements Resettable {

    private final AdaLexListener listener;

    public AdaLexHelper(AdaLexListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("`listener' is null");
        }
        this.listener = listener;
    }

    /**
     * Resets the instance to an initial state.
     */
    @Override
    public void reset() {
        // noop
    }

    /**
     * Write {@code value} to output -- if it contains any EOLs then the
     * {@code startNewLine()} is called in lieu of outputting EOL.
     */
    public void takeLiteral(String value, String linePrefix, String lineSuffix)
            throws IOException {

        if (linePrefix != null) listener.take(linePrefix);

        int off = 0;
        do {
            int w = 1, ri, ni, i;
            ri = value.indexOf("\r", off);
            ni = value.indexOf("\n", off);
            if (ri == -1 && ni == -1) {
                String sub = value.substring(off);
                listener.takeNonword(sub);
                break;
            }
            if (ri != -1 && ni != -1) {
                if (ri < ni) {
                    i = ri;
                    if (value.charAt(ri) == '\r' && value.charAt(ni) == '\n') {
                        w = 2;
                    }
                } else {
                    i = ni;
                }
            } else if (ri != -1) {
                i = ri;
            } else {
                i = ni;
            }

            String sub = value.substring(off, i);
            listener.takeNonword(sub);
            if (lineSuffix != null) listener.take(lineSuffix);
            listener.startNewLine();
            if (linePrefix != null) listener.take(linePrefix);
            off = i + w;
        } while (off < value.length());

        if (lineSuffix != null) listener.take(lineSuffix);
    }
}
