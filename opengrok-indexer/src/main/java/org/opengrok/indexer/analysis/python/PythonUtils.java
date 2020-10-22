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
package org.opengrok.indexer.analysis.python;

import java.util.regex.Pattern;
import org.opengrok.indexer.util.RegexUtils;

/**
 * Represents a container for Python-related utility methods.
 */
public class PythonUtils {

    /**
     * Matches an apostrophe followed by two more apostrophes as a Python
     * long-string delimiter and not following a backslash escape or following
     * an even number¹ of backslash escapes:
     * <pre>
     * {@code
     * \'(?=\'\') #...
     * }
     * </pre>
     * (Edit above and paste below [in NetBeans] for easy String escaping.)
     * <p>
     * ¹See {@link RegexUtils#getNotFollowingEscapePattern()} for a caveat
     * about the backslash assertion.
     */
    public static final Pattern LONGSTRING_APOS =
        Pattern.compile("\\'(?=\\'\\')" +
            RegexUtils.getNotFollowingEscapePattern());

    /** Private to enforce singleton. */
    private PythonUtils() {
    }
}
