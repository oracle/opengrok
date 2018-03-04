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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis.c;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the C keywords
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<String>();


    static {
        // CPP
        kwd.add("ident");
        kwd.add("ifndef");
        kwd.add("defined");
        kwd.add("endif");
        kwd.add("include");
        kwd.add("define");
        kwd.add("ifdef");
        kwd.add("pragma");

        // C keywords
        kwd.add("asm");
        kwd.add("auto");
        kwd.add("break");
        kwd.add("case");
        kwd.add("char");
        kwd.add("const");
        kwd.add("continue");
        kwd.add("default");
        kwd.add("do");
        kwd.add("double");
        kwd.add("else");
        kwd.add("enum");
        kwd.add("extern");
        kwd.add("float");
        kwd.add("for");
        kwd.add("goto");
        kwd.add("if");
        kwd.add("inline");
        kwd.add("int");
        kwd.add("long");
        kwd.add("register");
        kwd.add("restrict");
        kwd.add("return");
        kwd.add("short");
        kwd.add("signed");
        kwd.add("sizeof");
        kwd.add("static");
        kwd.add("struct");
        kwd.add("switch");
        kwd.add("typedef");
        kwd.add("union");
        kwd.add("unsigned");
        kwd.add("void");
        kwd.add("volatile");
        kwd.add("while");
        kwd.add("_Bool");
        kwd.add("_Complex");
        kwd.add("_Imaginary");
        // other keywords
        kwd.add("bool");
        kwd.add("true");
        kwd.add("false");
        kwd.add("redeclared");
    }
}
