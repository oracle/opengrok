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
package org.opengrok.indexer.analysis.fortran;

import java.util.regex.Pattern;

/**
 * Represents a container for Fortran-related utility methods.
 */
public class FortranUtils {

    /**
     * Matches an apostrophe that is not¹ part of a Fortran apostrophe escape
     * sequence:
     * <pre>
     * {@code
     * \'((?<=^.(?!\'))|(?<=[^\'].(?!\'))|(?<=^(\'\'){1,3}.(?!\'))|(?<=[^\'](\'\'){1,3}.(?!\')))
     * }
     * </pre>
     * (Edit above and paste below [in NetBeans] for easy String escaping.)
     * <p>
     * ¹Correctness in a long sequence of apostrophes is limited because Java
     * look-behind is not variable length but instead must have a definite
     * upper bound in the regex definition.
     */
    public static final Pattern CHARLITERAL_APOS_DELIMITER =
        Pattern.compile("\\'((?<=^.(?!\\'))|(?<=[^\\'].(?!\\'))|(?<=^(\\'\\'){1,3}.(?!\\'))|(?<=[^\\'](\\'\\'){1,3}.(?!\\')))");

    private FortranUtils() {
    }
}
