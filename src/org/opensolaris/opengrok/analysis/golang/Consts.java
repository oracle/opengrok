/*Patrick Lundquist
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
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis.golang;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the Golang keywords
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
    }
}
