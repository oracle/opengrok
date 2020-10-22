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
package org.opengrok.indexer.analysis.hcl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for HCL keywords and other string constants.
 */
public class Consts {

    private static final Set<String> kwd = new HashSet<>();

    public static final Set<String> KEYWORDS = Collections.unmodifiableSet(kwd);

    static {
        /*
         * HCL has the irritating aspect that "there are no globally-reserved
         * words, but in some contexts certain identifiers are reserved to
         * function as keywords."
         *
         * We'll just treat the following as globally reserved and wait to see
         * if that causes problems for any users.
         */

        kwd.add("false");
        kwd.add("true");
        kwd.add("null");
        kwd.add("for");
        kwd.add("endfor");
        kwd.add("in");
        kwd.add("if");
        kwd.add("else");
        kwd.add("endif");
    }

    /* private to enforce static */
    private Consts() {
    }
}
