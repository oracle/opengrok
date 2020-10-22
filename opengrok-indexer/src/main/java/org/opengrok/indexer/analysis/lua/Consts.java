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
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis.lua;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Lua keywords.
 * @author Evan Kinney
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    static {
        // Lua 5.3 Reference Manual, Chapter 3.1
        // http://www.lua.org/manual/5.3/manual.html
        kwd.add("and");
        kwd.add("break");
        kwd.add("do");
        kwd.add("else");
        kwd.add("elseif");
        kwd.add("end");
        kwd.add("false");
        kwd.add("for");
        kwd.add("function");
        kwd.add("goto");
        kwd.add("if");
        kwd.add("in");
        kwd.add("local");
        kwd.add("nil");
        kwd.add("not");
        kwd.add("or");
        kwd.add("repeat");
        kwd.add("return");
        kwd.add("then");
        kwd.add("true");
        kwd.add("until");
        kwd.add("while");
    }

    private Consts() {
    }

}
