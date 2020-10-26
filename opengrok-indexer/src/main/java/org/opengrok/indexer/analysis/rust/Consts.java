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
 * Portions Copyright (c) 2016, Nikolay Denev.
 */
package org.opengrok.indexer.analysis.rust;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Rust language keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    static {
        kwd.add("abstract");
        kwd.add("alignof");
        kwd.add("as");
        kwd.add("become");
        kwd.add("box");
        kwd.add("break");
        kwd.add("const");
        kwd.add("continue");
        kwd.add("crate");
        kwd.add("do");
        kwd.add("else");
        kwd.add("enum");
        kwd.add("extern");
        kwd.add("false");
        kwd.add("final");
        kwd.add("fn");
        kwd.add("for");
        kwd.add("if");
        kwd.add("impl");
        kwd.add("in");
        kwd.add("let");
        kwd.add("loop");
        kwd.add("macro");
        kwd.add("match");
        kwd.add("mod");
        kwd.add("move");
        kwd.add("mut");
        kwd.add("offsetof");
        kwd.add("override");
        kwd.add("priv");
        kwd.add("proc");
        kwd.add("pub");
        kwd.add("pure");
        kwd.add("ref");
        kwd.add("return");
        kwd.add("Self");
        kwd.add("self");
        kwd.add("sizeof");
        kwd.add("static");
        kwd.add("struct");
        kwd.add("super");
        kwd.add("trait");
        kwd.add("true");
        kwd.add("type");
        kwd.add("typeof");
        kwd.add("unsafe");
        kwd.add("unsized");
        kwd.add("use");
        kwd.add("virtual");
        kwd.add("where");
        kwd.add("while");
        kwd.add("yield");
    }

    private Consts() {
    }

}
