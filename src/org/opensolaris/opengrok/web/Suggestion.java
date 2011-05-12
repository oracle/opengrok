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
 * A simple container for search suggestions.
 * @author  Jens Elkner
 * @version $Revision$
 */
public class Suggestion {

    /** index name */
    public String name;
    /** freetext search suggestions */
    public String[] freetext;
    /** references search suggestions */
    public String[] refs;
    /** definitions search suggestions */
    public String[] defs;

    /**
     * Create a new suggestion.
     * @param name index name.
     */
    public Suggestion(String name) {
        this.name = name;
    }
}
