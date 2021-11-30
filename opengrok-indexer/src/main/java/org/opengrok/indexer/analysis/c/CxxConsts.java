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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis.c;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds static hash set containing the C keywords.
 */
public class CxxConsts {

    static final Set<String> kwd = new HashSet<>();

    static {
        // Add all the C keywords.
        kwd.addAll(Consts.kwd);

        // C++ keywords
        kwd.add("catch");
        kwd.add("class");
        kwd.add("const_cast");
        kwd.add("delete");
        kwd.add("dynamic_cast");
        kwd.add("explicit");
        kwd.add("friend");
        kwd.add("inline");
        kwd.add("mutable");
        kwd.add("namespace");
        kwd.add("new");
        kwd.add("operator");
        kwd.add("private");
        kwd.add("protected");
        kwd.add("public");
        kwd.add("reinterpret_cast");
        kwd.add("static_cast");
        kwd.add("template");
        kwd.add("this");
        kwd.add("throw");
        kwd.add("try");
        kwd.add("typeid");
        kwd.add("typename");
        kwd.add("using");
        kwd.add("virtual");
        kwd.add("wchar_t");
    }

    private CxxConsts() {
    }
}
