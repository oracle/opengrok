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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.swift;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Swift keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();

    static {
        kwd.add("associatedtype");
        kwd.add("class");
        kwd.add("deinit");
        kwd.add("enum");
        kwd.add("extension");
        kwd.add("fileprivate");
        kwd.add("func");
        kwd.add("import");
        kwd.add("init");
        kwd.add("inout");
        kwd.add("internal");
        kwd.add("let");
        kwd.add("open");
        kwd.add("operator");
        kwd.add("private");
        kwd.add("protocol");
        kwd.add("public");
        kwd.add("static");
        kwd.add("struct");
        kwd.add("subscript");
        kwd.add("typealias");
        kwd.add("var");
        kwd.add("break");
        kwd.add("case");
        kwd.add("continue");
        kwd.add("default");
        kwd.add("defer");
        kwd.add("do");
        kwd.add("else");
        kwd.add("fallthrough");
        kwd.add("for");
        kwd.add("guard");
        kwd.add("if");
        kwd.add("in");
        kwd.add("repeat");
        kwd.add("return");
        kwd.add("switch");
        kwd.add("where");
        kwd.add("while");
        kwd.add("as");
        kwd.add("Any");
        kwd.add("catch");
        kwd.add("false");
        kwd.add("is");
        kwd.add("nil");
        kwd.add("rethrows");
        kwd.add("super");
        kwd.add("self");
        kwd.add("Self");
        kwd.add("throw");
        kwd.add("throws");
        kwd.add("true");
        kwd.add("try");
        kwd.add("#available");
        kwd.add("#colorLiteral");
        kwd.add("#column");
        kwd.add("#else");
        kwd.add("#elseif");
        kwd.add("#endif");
        kwd.add("#file");
        kwd.add("#fileLiteral");
        kwd.add("#function");
        kwd.add("#if");
        kwd.add("#imageLiteral");
        kwd.add("#line");
        kwd.add("#selector");
        kwd.add("#sourceLocation");
        //TODO below might be context sensitive ones, should we still detect them?
        kwd.add("associativity");
        kwd.add("convenience");
        kwd.add("dynamic");
        kwd.add("didSet");
        kwd.add("final");
        kwd.add("get");
        kwd.add("infix");
        kwd.add("indirect");
        kwd.add("lazy");
        kwd.add("left");
        kwd.add("mutating");
        kwd.add("none");
        kwd.add("nonmutating");
        kwd.add("optional");
        kwd.add("override");
        kwd.add("postfix");
        kwd.add("precedence");
        kwd.add("prefix");
        kwd.add("Protocol");
        kwd.add("required");
        kwd.add("right");
        kwd.add("set");
        kwd.add("Type");
        kwd.add("unowned");
        kwd.add("weak");
        kwd.add("willSet");

        kwd.add("_"); // 4.0.3 "Keywords ... used in patterns"
    }

    private Consts() {
    }

}
