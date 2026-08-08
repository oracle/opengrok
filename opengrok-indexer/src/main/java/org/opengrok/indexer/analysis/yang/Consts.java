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
 * Copyright (c) 2026, nishank.soni <soninishank8@gmail.com>.
 */
package org.opengrok.indexer.analysis.yang;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * YANG statement keywords.
 */
public class Consts {

    private static final Set<String> kwd = new HashSet<>();

    public static final Set<String> KEYWORDS = Collections.unmodifiableSet(kwd);

    static {
        kwd.add("action");
        kwd.add("anydata");
        kwd.add("anyxml");
        kwd.add("argument");
        kwd.add("augment");
        kwd.add("base");
        kwd.add("belongs-to");
        kwd.add("bit");
        kwd.add("case");
        kwd.add("choice");
        kwd.add("config");
        kwd.add("contact");
        kwd.add("container");
        kwd.add("default");
        kwd.add("description");
        kwd.add("deviation");
        kwd.add("deviate");
        kwd.add("enum");
        kwd.add("error-app-tag");
        kwd.add("error-message");
        kwd.add("extension");
        kwd.add("feature");
        kwd.add("fraction-digits");
        kwd.add("grouping");
        kwd.add("identity");
        kwd.add("if-feature");
        kwd.add("import");
        kwd.add("include");
        kwd.add("input");
        kwd.add("key");
        kwd.add("leaf");
        kwd.add("leaf-list");
        kwd.add("length");
        kwd.add("list");
        kwd.add("mandatory");
        kwd.add("max-elements");
        kwd.add("min-elements");
        kwd.add("modifier");
        kwd.add("module");
        kwd.add("must");
        kwd.add("namespace");
        kwd.add("notification");
        kwd.add("ordered-by");
        kwd.add("organization");
        kwd.add("output");
        kwd.add("path");
        kwd.add("pattern");
        kwd.add("position");
        kwd.add("prefix");
        kwd.add("presence");
        kwd.add("range");
        kwd.add("reference");
        kwd.add("refine");
        kwd.add("require-instance");
        kwd.add("revision");
        kwd.add("revision-date");
        kwd.add("rpc");
        kwd.add("status");
        kwd.add("submodule");
        kwd.add("type");
        kwd.add("typedef");
        kwd.add("unique");
        kwd.add("units");
        kwd.add("uses");
        kwd.add("value");
        kwd.add("when");
        kwd.add("yang-version");
        kwd.add("yin-element");
    }

    private Consts() {
    }
}
