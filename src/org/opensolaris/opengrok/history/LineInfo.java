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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

/**
 * History information about a line in a source file.
 */
public class LineInfo {
    /** Revision for the last modification of the line. */
    private final String revision;
    /** Author responsible for the last modification of the line. */
    private final String author;

    /**
     * Create a <code>LineInfo</code> object.
     */
    LineInfo(String revision, String author) {
        this.revision = revision;
        this.author = author;
    }

    /**
     * Get the revision for the last modification of the line.
     * @return revision of last modification
     */
    public String getRevision() {
        return revision;
    }

    /**
     * Get the author responsible for the last modification of the line.
     * @return author responsible for last modification
     */
    public String getAuthor() {
        return author;
    }
}
