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
 */
package org.opengrok.indexer.analysis.python;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Python keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    static {
        kwd.add("and");
        kwd.add("as"); //2.5 , 2.6
        kwd.add("assert");
        kwd.add("break");
        kwd.add("class");
        kwd.add("continue");
        kwd.add("def");
        kwd.add("del");
        kwd.add("elif");
        kwd.add("else");
        kwd.add("except");
        kwd.add("exec");
        kwd.add("finally");
        kwd.add("for");
        kwd.add("from");
        kwd.add("global");
        kwd.add("if");
        kwd.add("import");
        kwd.add("in");
        kwd.add("is");
        kwd.add("lambda");
        kwd.add("not");
        kwd.add("or");
        kwd.add("pass");
        kwd.add("print");
        kwd.add("raise");
        kwd.add("return");
        kwd.add("try");
        kwd.add("while");
        kwd.add("with"); //2.5 , 2.6
        kwd.add("yield");
        kwd.add("None");  //2.4
    }

    private Consts() {
    }

}
