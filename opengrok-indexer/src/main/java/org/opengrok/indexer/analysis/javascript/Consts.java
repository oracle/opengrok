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
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.javascript;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds JavaScript keywords from ECMA-262 10th Edition, June 2019.
 */
public class Consts {

    private static final Set<String> kwd = new HashSet<>();

    public static final Set<String> KEYWORDS = Collections.unmodifiableSet(kwd);

    static {
        // literals
        kwd.add("true");
        kwd.add("false");
        kwd.add("null");
        //builtins
        kwd.add("Array");
        kwd.add("Boolean");
        kwd.add("Date");
        kwd.add("Function");
        kwd.add("Infinity"); // ECMA-262, 10th edition, June 2019
        kwd.add("Math");
        kwd.add("Number");
        kwd.add("Object");
        kwd.add("RegExp");
        kwd.add("String");                
        //keywords
        kwd.add("await"); // ECMA-262, 10th edition, June 2019
        kwd.add("break");
        kwd.add("case");
        kwd.add("catch");
        kwd.add("class");
        kwd.add("const");
        kwd.add("continue");
        kwd.add("debugger");
        kwd.add("default");
        kwd.add("delete");
        kwd.add("do");
        kwd.add("else");
        kwd.add("export");
        kwd.add("extends");
        kwd.add("finally");
        kwd.add("for");
        kwd.add("function");
        kwd.add("if");
        kwd.add("in");
        kwd.add("instanceof");
        kwd.add("import");
        kwd.add("new");
        kwd.add("return");
        kwd.add("super");
        kwd.add("switch");
        kwd.add("this");
        kwd.add("throw");
        kwd.add("try");
        kwd.add("typeof");
        kwd.add("var");
        kwd.add("void");
        kwd.add("while");
        kwd.add("with");
        kwd.add("yield");
        //future reserved
        kwd.add("enum");
        //strict future reserved
        kwd.add("implements");
        kwd.add("interface");
        kwd.add("let");
        kwd.add("package");
        kwd.add("private");
        kwd.add("protected");
        kwd.add("public");
        kwd.add("static");
    }

    protected Consts() {
    }
}
