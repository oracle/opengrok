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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.golang;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Golang keywords.
 * @author Patrick Lundquist
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    static {
        // Go Programming Language Specification 2015
        kwd.add("break");
        kwd.add("case");
        kwd.add("chan");
        kwd.add("const");
        kwd.add("continue");
        kwd.add("default");
        kwd.add("defer");
        kwd.add("else");
        kwd.add("fallthrough");
        kwd.add("for");
        kwd.add("func");
        kwd.add("go");
        kwd.add("goto");
        kwd.add("if");
        kwd.add("import");
        kwd.add("interface");
        kwd.add("map");
        kwd.add("package");
        kwd.add("range");
        kwd.add("return");
        kwd.add("select");
        kwd.add("struct");
        kwd.add("switch");
        kwd.add("type");
        kwd.add("var");

        kwd.add("_"); // Blank identifier

        kwd.add("bool"); // Predeclared identifiers: Types
        kwd.add("byte"); // Predeclared identifiers: Types
        kwd.add("complex64"); // Predeclared identifiers: Types
        kwd.add("complex128"); // Predeclared identifiers: Types
        kwd.add("error"); // Predeclared identifiers: Types
        kwd.add("float32"); // Predeclared identifiers: Types
        kwd.add("float64"); // Predeclared identifiers: Types
        kwd.add("int"); // Predeclared identifiers: Types
        kwd.add("int8"); // Predeclared identifiers: Types
        kwd.add("int16"); // Predeclared identifiers: Types
        kwd.add("int32"); // Predeclared identifiers: Types
        kwd.add("int64"); // Predeclared identifiers: Types
        kwd.add("rune"); // Predeclared identifiers: Types
        kwd.add("string"); // Predeclared identifiers: Types
        kwd.add("uint"); // Predeclared identifiers: Types
        kwd.add("uint8"); // Predeclared identifiers: Types
        kwd.add("uint16"); // Predeclared identifiers: Types
        kwd.add("uint32"); // Predeclared identifiers: Types
        kwd.add("uint64"); // Predeclared identifiers: Types
        kwd.add("uintptr"); // Predeclared identifiers: Types

        kwd.add("true"); // Predeclared identifiers: Constants
        kwd.add("false"); // Predeclared identifiers: Constants
        kwd.add("iota"); // Predeclared identifiers: Constants

        kwd.add("nil"); // Predeclared identifiers: Zero value

        kwd.add("append"); // Predeclared identifiers: Functions
        kwd.add("cap"); // Predeclared identifiers: Functions
        kwd.add("close"); // Predeclared identifiers: Functions
        kwd.add("complex"); // Predeclared identifiers: Functions
        kwd.add("copy"); // Predeclared identifiers: Functions
        kwd.add("delete"); // Predeclared identifiers: Functions
        kwd.add("imag"); // Predeclared identifiers: Functions
        kwd.add("len"); // Predeclared identifiers: Functions
        kwd.add("make"); // Predeclared identifiers: Functions
        kwd.add("new"); // Predeclared identifiers: Functions
        kwd.add("panic"); // Predeclared identifiers: Functions
        kwd.add("print"); // Predeclared identifiers: Functions
        kwd.add("println"); // Predeclared identifiers: Functions
        kwd.add("real"); // Predeclared identifiers: Functions
        kwd.add("recover"); // Predeclared identifiers: Functions
    }

    private Consts() {
    }

}
