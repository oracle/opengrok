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
package org.opengrok.indexer.analysis.haskell;

import java.util.regex.Pattern;

/**
 * Represents a container for Haskell-related utility methods.
 */
public class HaskellUtils {

    /**
     * Matches either the end of a Haskell nested comment or a superfluous
     * right curly bracket (which is not a valid BrowseableURI character)
     * captured in order to be able to detect the end of a Haskell nested comment.
     */
    public static final Pattern MAYBE_END_NESTED_COMMENT = Pattern.compile("\\-?\\}");

    /** Private to enforce static. */
    private HaskellUtils() {
    }
}
