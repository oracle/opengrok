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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2011, Jens Elkner.
 */
package org.opengrok.indexer.web;

/**
 * A simple container for search suggestions.
 * @author  Jens Elkner
 * @version $Revision$
 */
public class Suggestion {

    /** index name. */
    private final String name;
    /** freetext search suggestions. */
    private String[] freetext;
    /** references search suggestions. */
    private String[] refs;
    /** definitions search suggestions. */
    private String[] defs;

    /**
     * Create a new suggestion.
     * @param name index name.
     */
    public Suggestion(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String[] getFreetext() {
        return freetext;
    }

    public String[] getRefs() {
        return refs;
    }

    public String[] getDefs() {
        return defs;
    }

    public void setFreetext(String[] freetext) {
        this.freetext = freetext;
    }

    public void setRefs(String[] refs) {
        this.refs = refs;
    }

    public void setDefs(String[] defs) {
        this.defs = defs;
    }

    /**
     * @return true if at least one of the properties has some content, false otherwise
     */
    public boolean isUsable() {
        return (freetext != null && freetext.length > 0)
                || (defs != null && defs.length > 0)
                || (refs != null && refs.length > 0);
    }
}
