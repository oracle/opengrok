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
 * Copyright (c) 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.web;

/**
 * Sort orders recognized in the web UI.
 *
 * @author  Jens Elkner
 * @version $Revision$
 */
public enum SortOrder {

    /** sort by last modification time */
    LASTMODIFIED("lastmodtime", "last modified time"),
    /** sort by relevancy */
    RELEVANCY("relevancy", "relevance"),
    /** sort by path */
    BY_PATH("fullpath", "path");
    private String name;
    private String desc;

    private SortOrder(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    /**
     * Get the Sort order wrt. the given name.
     * @param name the query parameter name of the order to find.
     * @return {@code null} if there is no SortOrder with the given name,
     *  the corresponding SortOrder otherwise.
     * @see #toString()
     */
    public static SortOrder get(String name) {
        if (name == null || name.length() == 0) {
            return null;
        }
        for (SortOrder s : values()) {
            if (name.equals(s.name)) {
                return s;
            }
        }
        return null;
    }

    /**
     * The query parameter name.
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * A more user friendly description (UI name) of the sort order.
     * @return a very short description.
     */
    public String getDesc() {
        return desc;
    }
}
