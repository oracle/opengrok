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

package org.opensolaris.opengrok.analysis;

import java.io.IOException;

/**
 * Represents an abstract base class and subclass of {@link JFlexXref} whose
 * lex states simply mirror disjoint document spans in the output.
 */
public abstract class JFlexXrefSimple extends JFlexXref {

    /**
     * Starts a new {@link #disjointSpan(java.lang.String)}, and sets a new
     * state with {@link #yypush(int)}.
     * @param newState state id
     * @param className span class name
     * @throws java.io.IOException if an error occurs writing the new tag
     */
    public void pushSpan(int newState, String className) throws IOException {
        disjointSpan(className);
        super.yypush(newState);
    }

    /**
     * Overrides to close the disjoint span before {@link #yypop()}.
     * @throws IOException if an error occurs writing the closing of the tag
     */
    @Override
    public void yypop() throws IOException {
        disjointSpan(null);
        super.yypop();
    }
}
