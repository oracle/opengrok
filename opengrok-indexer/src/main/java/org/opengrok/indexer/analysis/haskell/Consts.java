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
package org.opengrok.indexer.analysis.haskell;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Haskell keywords.
 * @author Harry Pan
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();
    static {
        // Haskell 2010 Language Report, Chapter 2.4
        kwd.add("case");
        kwd.add("class");
        kwd.add("data");
        kwd.add("default");
        kwd.add("deriving");
        kwd.add("do");
        kwd.add("else");
        kwd.add("foreign");
        kwd.add("if");
        kwd.add("import");
        kwd.add("in");
        kwd.add("infix");
        kwd.add("infixl");
        kwd.add("infixr");
        kwd.add("instance");
        kwd.add("let");
        kwd.add("module");
        kwd.add("newtype");
        kwd.add("of");
        kwd.add("then");
        kwd.add("type");
        kwd.add("where");

        kwd.add("_"); // 2.4 Identifiers and Operators
    }

    private Consts() {
    }

}
