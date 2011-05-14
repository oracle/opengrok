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
 * Copyright 2009 - 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.web;

/**
 * Known diff display types.
 *
 * @author  Jens Elkner
 * @version $Revision$
 */
public enum DiffType {

    /** side-by-side diff */
    SIDEBYSIDE('s', "sdiff"),
    /** unified diff (i.e. with context lines) */
    UNIFIED('u', "udiff"),
    /** traditional ed diff (no context lines) */
    TEXT('t', "text"),
    /** the old version of the file (before changes applied) */
    OLD('o', "old"),
    /** the new version of the file (after changes applied) */
    NEW('n', "new");
    private char abbrev;
    private String name;

    private DiffType(char abbrev, String name) {
        this.abbrev = abbrev;
        this.name = name;
    }

    /**
     * Get the diff type for the given abbreviation.
     * @param c abbreviation to check.
     * @return {@code null} if not found, the diff type otherwise.
     */
    public static final DiffType get(char c) {
        for (DiffType d : values()) {
            if (c == d.abbrev) {
                return d;
            }
        }
        return null;
    }

    /**
     * Get the diff type for the given abbreviation or name.
     * @param c abbreviation or name to check.
     * @return {@code null} if not found, the diff type otherwise.
     */
    public static final DiffType get(String c) {
        if (c == null || c.length() == 0) {
            return null;
        }
        if (c.length() == 1) {
            return get(c.charAt(0));
        }
        for (DiffType d : values()) {
            if (d.name.equals(c)) {
                return d;
            }
        }
        // fallback to first char
        return get(c.charAt(0));
    }

    /**
     * Get the abbreviation to be used for this diff type.
     * @return wrt. to all known diff types a unique character.
     */
    public char getAbbrev() {
        return abbrev;
    }

    /**
     * {@inheritDoc}
     * @return the common name of the diff type.
     */
    @Override
    public String toString() {
        return name;
    }
}
