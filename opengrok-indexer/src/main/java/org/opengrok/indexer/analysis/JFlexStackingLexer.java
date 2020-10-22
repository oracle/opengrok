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
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;

/**
 * Represents an API for an extension of {@link JFlexLexer} that needs to track
 * a state stack.
 */
public interface JFlexStackingLexer extends JFlexLexer {

    /**
     * Saves current {@link #yystate()} to stack, and enters the specified
     * {@code newState} with {@link #yybegin(int)}.
     * @param newState state id
     */
    void yypush(int newState);

    /**
     * Pops the last state from the stack, and enters the state with
     * {@link #yybegin(int)}.
     * @throws IOException if any error occurs while effecting the pop
     */
    void yypop() throws IOException;

    /**
     * Gets the yychar value.
     */
    long getYYCHAR();

    /**
     * Gets the YYEOF value.
     */
    int getYYEOF();

    /**
     * Gets the line number.
     */
    int getLineNumber();

    /**
     * Tests if the instance's state stack is empty.
     * @return {@code true} if the stack contains no items; {@code false}
     * otherwise.
     */
    boolean emptyStack();
}
