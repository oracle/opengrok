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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import java.io.IOException;

/**
 * Represents an abstract base class for OpenGrok query-building term-
 * transformers.
 */
abstract class TermEscaperBase {

    private StringBuilder out;

    /**
     * "Runs the scanner [as documented by JFlex].
     * <p>[The method] can be used to get the next token from the input."
     * <p>"Consume[s] input until one of the expressions in the specification
     * is matched or an error occurs."
     * @return a value returned by the lexer specification if defined or the
     * {@code EOF} value upon reading end-of-file
     * @throws IOException if an error occurs reading the input
     */
    abstract boolean yylex() throws IOException;

    /**
     * @param out the target to append
     */
    void setOut(StringBuilder out) {
        this.out = out;
    }

    void appendOut(char c) {
        out.append(c);
    }

    void appendOut(String s) {
        out.append(s);
    }

    /**
     * Call {@link #yylex()} until {@code false}, which consumes all input so
     * that the argument to {@link #setOut(StringBuilder)} contains the entire
     * transformation.
     */
    void consume() {
        try {
            while (yylex()) {
                //noinspection UnnecessaryContinue
                continue;
            }
        } catch (IOException ex) {
            // cannot get here with StringBuilder operations
        }
    }
}
