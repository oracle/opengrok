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
 * Copyright (c) 2025, Yelisey Romanov <progoramur@gmail.com>.
 */
package org.opengrok.indexer.analysis.ocaml;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for a set of OCaml keywords.
 */
public class Consts {

    static final Set<String> kwd = new HashSet<>();

    static {
        /* OCaml 5.3.0 keywords */
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
        kwd.add("ref");
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

        /* OCaml 5.3.0 predefined types */
        /* it is possible to make a variable of such a name,
           though people mostly do not use this opportunity */
        kwd.add("bool");
        kwd.add("char");
        kwd.add("float");
        kwd.add("int");

        kwd.add("bytes");
        kwd.add("string");

        kwd.add("array");
        kwd.add("list");
        kwd.add("option");
        /* "result" is often a variable, so not adding */

        kwd.add("unit");
    }

    /** Private to enforce static. */
    private Consts() {
    }
}
