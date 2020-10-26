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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.r;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for R keywords and other string constants.
 */
public class Consts {

    private static final Set<String> kwd = new HashSet<>();

    public static final Set<String> KEYWORDS = Collections.unmodifiableSet(kwd);

    static {
        kwd.add("break");
        kwd.add("else");
        kwd.add("FALSE");
        kwd.add("for");
        kwd.add("function");
        kwd.add("if");
        kwd.add("in");
        kwd.add("Inf");
        kwd.add("NA");
        kwd.add("NA_character_");
        kwd.add("NA_complex_");
        kwd.add("NA_integer_");
        kwd.add("NA_real_");
        kwd.add("NaN");
        kwd.add("next");
        kwd.add("NULL");
        kwd.add("repeat");
        kwd.add("TRUE");
        kwd.add("while");
    }

    /* private to enforce static */
    private Consts() {
    }
}
