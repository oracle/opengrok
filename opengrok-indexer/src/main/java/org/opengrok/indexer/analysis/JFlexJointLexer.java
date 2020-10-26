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
package org.opengrok.indexer.analysis;

import java.io.IOException;

/**
 * Represents an API for JFlex lexers that produce multiple types of derived
 * OpenGrok documents (e.g., cross-reference documents [xrefs] or Lucene search
 * documents [tokenizers]) from the same JFlex productions.
 */
public interface JFlexJointLexer extends JFlexStackingLexer {

    /**
     * Passes non-symbolic fragment for processing.
     * @param value the excised fragment
     * @throws IOException if an error occurs while accepting
     */
    void offer(String value) throws IOException;

    /**
     * Passes a text fragment that is syntactically a symbol for processing.
     * @param value the excised symbol
     * @param captureOffset the offset from yychar where {@code value} began
     * @param ignoreKwd a value indicating whether keywords should be ignored
     * @return true if the {@code value} was not in keywords or if the
     * {@code ignoreKwd} was true
     * @throws IOException if an error occurs while accepting
     */
    boolean offerSymbol(String value, int captureOffset, boolean ignoreKwd)
        throws IOException;

    /**
     * Indicates that something unusual happened where normally a symbol would
     * have been offered.
     */
    void skipSymbol();

    /**
     * Passes a text fragment that is syntactically a keyword symbol for
     * processing.
     * @param value the excised symbol
     * @throws IOException if an error occurs while accepting
     */
    void offerKeyword(String value) throws IOException;

    /**
     * Indicates that the current line is ended.
     * @throws IOException if an error occurs when handling the EOL
     */
    void startNewLine() throws IOException;

    /**
     * Indicates the closing of an open tag and the opening -- if
     * {@code className} is non-null -- of a new one.
     * @param className the class name for the new tag or {@code null} just to
     * close an open tag.
     * @throws IOException if an output error occurs
     */
    void disjointSpan(String className) throws IOException;

    /**
     * Indicates that eligible source code was encountered for physical
     * lines-of-code count (physical LOC).
     */
    void phLOC();
}
