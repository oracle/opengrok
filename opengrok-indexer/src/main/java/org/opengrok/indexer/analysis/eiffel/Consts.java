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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.eiffel;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for a set of Eiffel keywords.
 */
public class Consts {

    public static final Set<String> kwd = new HashSet<>();

    static {
        /*
         * The following are defined with specific non-lower casing to
         * distinguish them as non-key words (e.g., "TUPLE"), but Eiffel is
         * case-insensitive per 8.2.19 Semantics: Case Insensitivity principle.
         * For OpenGrok Consts purposes, therefore, `kwd' is all lower-cased.
         */
        kwd.add("current"); // 8.32.13 Definition: Reserved (non-key)word
        kwd.add("false"); // 8.32.13 Definition: Reserved (non-key)word
        kwd.add("precursor"); // 8.32.13 Definition: Reserved (non-key)word
        kwd.add("result"); // 8.32.13 Definition: Reserved (non-key)word
        kwd.add("tuple"); // 8.32.13 Definition: Reserved (non-key)word
        kwd.add("true"); // 8.32.13 Definition: Reserved (non-key)word
        kwd.add("void"); // 8.32.13 Definition: Reserved (non-key)word

        kwd.add("agent"); // 8.32.13 Definition: Reserved keyword
        kwd.add("alias"); // 8.32.13 Definition: Reserved keyword
        kwd.add("all"); // 8.32.13 Definition: Reserved keyword
        kwd.add("and"); // 8.32.13 Definition: Reserved keyword
        kwd.add("as"); // 8.32.13 Definition: Reserved keyword
        kwd.add("assign"); // 8.32.13 Definition: Reserved keyword
        kwd.add("attribute"); // 8.32.13 Definition: Reserved keyword
        kwd.add("check"); // 8.32.13 Definition: Reserved keyword
        kwd.add("class"); // 8.32.13 Definition: Reserved keyword
        kwd.add("convert"); // 8.32.13 Definition: Reserved keyword
        kwd.add("create"); // 8.32.13 Definition: Reserved keyword
        kwd.add("debug"); // 8.32.13 Definition: Reserved keyword
        kwd.add("deferred"); // 8.32.13 Definition: Reserved keyword
        kwd.add("do"); // 8.32.13 Definition: Reserved keyword
        kwd.add("else"); // 8.32.13 Definition: Reserved keyword
        kwd.add("elseif"); // 8.32.13 Definition: Reserved keyword
        kwd.add("end"); // 8.32.13 Definition: Reserved keyword
        kwd.add("ensure"); // 8.32.13 Definition: Reserved keyword
        kwd.add("expanded"); // 8.32.13 Definition: Reserved keyword
        kwd.add("export"); // 8.32.13 Definition: Reserved keyword
        kwd.add("external"); // 8.32.13 Definition: Reserved keyword
        kwd.add("feature"); // 8.32.13 Definition: Reserved keyword
        kwd.add("from"); // 8.32.13 Definition: Reserved keyword
        kwd.add("frozen"); // 8.32.13 Definition: Reserved keyword
        kwd.add("if"); // 8.32.13 Definition: Reserved keyword
        kwd.add("implies"); // 8.32.13 Definition: Reserved keyword
        kwd.add("inherit"); // 8.32.13 Definition: Reserved keyword
        kwd.add("inspect"); // 8.32.13 Definition: Reserved keyword
        kwd.add("invariant"); // 8.32.13 Definition: Reserved keyword
        kwd.add("like"); // 8.32.13 Definition: Reserved keyword
        kwd.add("local"); // 8.32.13 Definition: Reserved keyword
        kwd.add("loop"); // 8.32.13 Definition: Reserved keyword
        kwd.add("not"); // 8.32.13 Definition: Reserved keyword
        kwd.add("note"); // 8.32.13 Definition: Reserved keyword
        kwd.add("obsolete"); // 8.32.13 Definition: Reserved keyword
        kwd.add("old"); // 8.32.13 Definition: Reserved keyword
        kwd.add("once"); // 8.32.13 Definition: Reserved keyword
        kwd.add("only"); // 8.32.13 Definition: Reserved keyword
        kwd.add("or"); // 8.32.13 Definition: Reserved keyword
        kwd.add("redefine"); // 8.32.13 Definition: Reserved keyword
        kwd.add("rename"); // 8.32.13 Definition: Reserved keyword
        kwd.add("require"); // 8.32.13 Definition: Reserved keyword
        kwd.add("rescue"); // 8.32.13 Definition: Reserved keyword
        kwd.add("retry"); // 8.32.13 Definition: Reserved keyword
        kwd.add("select"); // 8.32.13 Definition: Reserved keyword
        kwd.add("separate"); // 8.32.13 Definition: Reserved keyword
        kwd.add("then"); // 8.32.13 Definition: Reserved keyword
        kwd.add("undefine"); // 8.32.13 Definition: Reserved keyword
        kwd.add("until"); // 8.32.13 Definition: Reserved keyword
        kwd.add("variant"); // 8.32.13 Definition: Reserved keyword
        kwd.add("when"); // 8.32.13 Definition: Reserved keyword
        kwd.add("xor"); // 8.32.13 Definition: Reserved keyword
    }

    /** Private to enforce static. */
    private Consts() {
    }
}
