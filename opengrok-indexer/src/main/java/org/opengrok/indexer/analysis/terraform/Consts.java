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
package org.opengrok.indexer.analysis.terraform;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a container for Terraform keywords and other string constants.
 */
public class Consts {

    private static final Set<String> kwd = new HashSet<>();
    private static final Set<String> pathKwd = new HashSet<>();

    static final Set<String> KEYWORDS = Collections.unmodifiableSet(kwd);
    static final Set<String> PATH_KEYWORDS = Collections.unmodifiableSet(pathKwd);

    static {
        /*
         * HCL has the irritating aspect that "there are no globally-reserved
         * words, but in some contexts certain identifiers are reserved to
         * function as keywords."
         *
         * We'll just treat the following as globally reserved and wait to see
         * if that causes problems for any users.
         */
        kwd.addAll(org.opengrok.indexer.analysis.hcl.Consts.KEYWORDS);

        /*
         * We will support some `path.` context-specific keywords though.
         */
        pathKwd.add("module");
        pathKwd.add("root");
        pathKwd.add("cwd");
    }

    /* private to enforce static */
    private Consts() {
    }
}
