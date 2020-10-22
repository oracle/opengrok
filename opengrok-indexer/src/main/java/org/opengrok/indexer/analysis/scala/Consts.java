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
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.scala;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Scala keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    static {
        kwd.add("abstract");
        kwd.add("case");
        kwd.add("catch");
        kwd.add("class");
        kwd.add("def");
        kwd.add("do");
        kwd.add("else");
        kwd.add("extends");
        kwd.add("false");
        kwd.add("final");
        kwd.add("finally");
        kwd.add("for");
        kwd.add("forSome");
        kwd.add("if");
        kwd.add("implicit");
        kwd.add("import");
        kwd.add("lazy");
        kwd.add("match");
        kwd.add("new");
        kwd.add("null");
        kwd.add("object");
        kwd.add("override");
        kwd.add("package");
        kwd.add("private");
        kwd.add("protected");
        kwd.add("return");
        kwd.add("sealed");
        kwd.add("super");
        kwd.add("this");
        kwd.add("throw");
        kwd.add("trait");
        kwd.add("try");
        kwd.add("true");
        kwd.add("type");
        kwd.add("val");
        kwd.add("var");
        kwd.add("while");
        kwd.add("with");
        kwd.add("yield");

        kwd.add("_"); // "Lexical syntax ... reserved words"
    }

    private Consts() {
    }

}
