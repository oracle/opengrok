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
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2011 Jens Elkner.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.php;

import java.io.IOException;
import java.util.Stack;
import org.opensolaris.opengrok.analysis.JFlexXref;

/**
 * Represents an abstract base class and subclass of {@link JFlexXref} for the
 * PHP-specific methodology of parsing and stacking "pop strings."
 */
public abstract class PhpXrefSpanner extends JFlexXref {

    protected final Stack<String> popStrings = new Stack<>();

    /**
     * save current yy state to stack
     * @param newState state id
     * @param popString string for the state
     */
    public void yypush(int newState, String popString) {
        super.yypush(newState);
        popStrings.push(popString);
    }

    /**
     * save current yy state to stack
     * @param newState state id
     */
    @Override
    public void yypush(int newState) {
        yypush(newState, null);
    }

    /**
     * pop last state from stack
     * @throws IOException in case of any I/O problem
     */
    @Override
    public void yypop() throws IOException {
        String popString = popStrings.pop();
        if (popString != null) {
            out.write(popString);
        }
        super.yypop();
    }

    @Override
    protected void clearStack() {
        super.clearStack();
        popStrings.clear();
    }
}
