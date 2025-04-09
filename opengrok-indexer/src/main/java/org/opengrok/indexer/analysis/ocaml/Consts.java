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
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.ocaml;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for a set of OCaml keywords.
 */
public class Consts {

    static final Set<String> kwd = new HashSet<>();

    /* From parsing/lexer.mll of OCaml 5.3.0. */
    static {
        kwd.add("and");
        kwd.add("as");
        kwd.add("assert");
        kwd.add("begin");
        kwd.add("class");
        kwd.add("constraint");
        kwd.add("do");
        kwd.add("done");
        kwd.add("downto");
        kwd.add("effect");
        kwd.add("else");
        kwd.add("end");
        kwd.add("exception");
        kwd.add("external");
        kwd.add("false");
        kwd.add("for");
        kwd.add("fun");
        kwd.add("function");
        kwd.add("functor");
        kwd.add("if");
        kwd.add("in");
        kwd.add("include");
        kwd.add("inherit");
        kwd.add("initializer");
        kwd.add("lazy");
        kwd.add("let");
        kwd.add("match");
        kwd.add("method");
        kwd.add("module");
        kwd.add("mutable");
        kwd.add("new");
        kwd.add("nonrec");
        kwd.add("object");
        kwd.add("of");
        kwd.add("open");
        kwd.add("or");
        kwd.add("parser");
        kwd.add("private");
        kwd.add("rec");
        kwd.add("sig");
        kwd.add("struct");
        kwd.add("then");
        kwd.add("to");
        kwd.add("true");
        kwd.add("try");
        kwd.add("type");
        kwd.add("val");
        kwd.add("virtual");
        kwd.add("when");
        kwd.add("while");
        kwd.add("with");
        kwd.add("lor");
        kwd.add("lxor");
        kwd.add("mod");
        kwd.add("land");
        kwd.add("lsl");
        kwd.add("lsr");
        kwd.add("asr");
    }

    /** Private to enforce static. */
    private Consts() {
    }
}
