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
 */
package org.opengrok.indexer.analysis.kotlin;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Kotlin keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();

    static {
        
        //TODO
        // it is a keyword for lambdas with 1 param
        
        kwd.add("abstract");
        kwd.add("annotation");
        kwd.add("as");
        kwd.add("break");
        kwd.add("by");
        kwd.add("catch");
        kwd.add("class");
        kwd.add("companion");
        kwd.add("const");
        kwd.add("constructor");
        kwd.add("continue");
        kwd.add("crossinline");
        kwd.add("data");
        kwd.add("do");
        kwd.add("dynamic");
        kwd.add("else");
        kwd.add("enum");
        kwd.add("external");
        kwd.add("false");
        kwd.add("final");
        kwd.add("finally");
        kwd.add("for");
        kwd.add("fun");
        kwd.add("get");
        kwd.add("if");
        kwd.add("import");
        kwd.add("in");
        kwd.add("infix");
        kwd.add("inline");
        kwd.add("inner");
        kwd.add("interface");
        kwd.add("internal");
        kwd.add("is");
        kwd.add("lateinit");
        kwd.add("noinline");
        kwd.add("null");
        kwd.add("nullobject");
        kwd.add("object");        
        kwd.add("open");
        kwd.add("operator");
        kwd.add("out");
        kwd.add("override");
        kwd.add("package");
        kwd.add("private");
        kwd.add("protected");
        kwd.add("public");
        kwd.add("reified");
        kwd.add("return");
        kwd.add("sealed");
        kwd.add("set");
        kwd.add("super");        
        kwd.add("tailrec");
        kwd.add("this");
        kwd.add("throw");
        kwd.add("true");
        kwd.add("try");
        kwd.add("typealias");
        kwd.add("typeof");
        kwd.add("val");
        kwd.add("var");
        kwd.add("vararg");
        kwd.add("when");
        kwd.add("where");
        kwd.add("while");
    }

    private Consts() {
    }

}
