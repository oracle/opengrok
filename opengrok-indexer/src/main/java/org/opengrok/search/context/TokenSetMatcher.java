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
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.search.context;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class TokenSetMatcher extends LineMatcher {
    private final Set<String> tokenSet;

    public TokenSetMatcher(Set<String> tokenSet, boolean caseInsensitive) {
        super(caseInsensitive);

        // Use a TreeSet with an explicit comparator to allow for case
        // insensitive lookups in the set if this is a case insensitive
        // matcher.
        this.tokenSet = new TreeSet<String>(new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return compareStrings(s1, s2);
            }
        });

        this.tokenSet.addAll(tokenSet);
    }

    public int match(String token) {
        return tokenSet.contains(token) ? MATCHED : NOT_MATCHED;
    }
}
