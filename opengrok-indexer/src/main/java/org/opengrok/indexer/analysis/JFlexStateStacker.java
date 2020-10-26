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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;
import java.util.Stack;

/**
 * Represents an abstract base class for resettable lexers that need to track
 * a state stack.
 */
public abstract class JFlexStateStacker implements Resettable,
    JFlexStackingLexer {

    protected final Stack<Integer> stack = new Stack<>();

    protected int lineNumber = 1;

    /**
     * Resets the instance using {@link #clearStack()}, and sets line number to
     * one.
     */
    public void reset() {
        clearStack();
        setLineNumber(1);
    }

    /**
     * Saves current {@link #yystate()} to stack, and enters the specified
     * {@code newState} with {@link #yybegin(int)}.
     * @param newState state id
     */
    public void yypush(int newState) {
        this.stack.push(yystate());
        yybegin(newState);
    }

    /**
     * Pops the last state from the stack, and enters the state with
     * {@link #yybegin(int)}.
     * @throws IOException if any error occurs while effecting the pop
     */
    public void yypop() throws IOException {
        yybegin(this.stack.pop());
    }

    /**
     * Calls {@link #clearStack()}, and enters the specified {@code newState}
     * with {@link #yybegin(int)}.
     * @param newState state id
     */
    public void yyjump(int newState) {
        clearStack();
        yybegin(newState);
    }

    /**
     * Gets the YYEOF value.
     * @return YYEOF
     */
    public abstract int getYYEOF();

    /**
     * Gets the line number. Default value is 1.
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Tests if the instance's state stack is empty.
     * @return {@code true} if the stack contains no items; {@code false}
     * otherwise.
     */
    public boolean emptyStack() {
        return stack.empty();
    }

    /**
     * Sets the line number.
     */
    protected void setLineNumber(int value) {
        lineNumber = value;
    }

    /**
     * Clears the instance stack.
     */
    protected void clearStack() {
        stack.clear();
    }
}
