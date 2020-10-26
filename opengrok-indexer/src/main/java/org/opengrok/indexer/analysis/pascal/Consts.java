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
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.pascal;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Pascal keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    static {
        kwd.add("abstract");
        kwd.add("and");
        kwd.add("array");
        kwd.add("as");
        kwd.add("asm");
        kwd.add("begin");
        kwd.add("boolean");
        kwd.add("break");
        kwd.add("case");
        kwd.add("class");
        kwd.add("const");
        kwd.add("constructor");
        kwd.add("continue");
        kwd.add("default");
        kwd.add("destructor");
        kwd.add("dispinterface");
        kwd.add("dispose");
        kwd.add("div");
        kwd.add("do");
        kwd.add("double");
        kwd.add("downto");
        kwd.add("else");
        kwd.add("end");
        kwd.add("except");
        kwd.add("exit");
        kwd.add("exports");
        kwd.add("false");
        kwd.add("file");
        kwd.add("finalization");
        kwd.add("finally");
        kwd.add("for");
        kwd.add("function");
        kwd.add("goto");
        kwd.add("if");
        kwd.add("implementation");
        kwd.add("in");
        kwd.add("inherited");
        kwd.add("initialization");
        kwd.add("inline");
        kwd.add("integer");
        kwd.add("interface");
        kwd.add("is");
        kwd.add("label");
        kwd.add("library");
        kwd.add("mod");
        kwd.add("new");
        kwd.add("nil");
        kwd.add("not");
        kwd.add("object");
        kwd.add("of");
        kwd.add("on");
        kwd.add("operator");
        kwd.add("or");
        kwd.add("out");
        kwd.add("override");
        kwd.add("packed");
        kwd.add("private");
        kwd.add("procedure");
        kwd.add("program");
        kwd.add("property");
        kwd.add("protected");
        kwd.add("public");
        kwd.add("published");
        kwd.add("raise");
        kwd.add("read");
        kwd.add("record");
        kwd.add("repeat");
        kwd.add("resourcestring");
        kwd.add("self");
        kwd.add("set");
        kwd.add("shl");
        kwd.add("shr");
        kwd.add("strict");
        kwd.add("string");
        kwd.add("then");
        kwd.add("threadvar");
        kwd.add("to");
        kwd.add("true");
        kwd.add("try");
        kwd.add("type");
        kwd.add("unit");
        kwd.add("until");
        kwd.add("uses");
        kwd.add("var");
        kwd.add("virtual");
        kwd.add("while");
        kwd.add("with");
        kwd.add("write");
        kwd.add("xor");

        kwd.add("absolute"); // Reconcile w. Turbo Pascal
        kwd.add("reintroduce"); // Reconcile w. Turbo Pascal
    }

    private Consts() {
    }

}
