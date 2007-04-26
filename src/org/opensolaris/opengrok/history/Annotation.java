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

import java.util.ArrayList;

/**
 * Class representing file annotation, i.e., revision and author for the last
 * modification of each line in the file.
 */
public class Annotation {

    private final ArrayList<Line> lines = new ArrayList<Line>();
    private int widestRevision;
    private int widestAuthor;

    /**
     * Gets the revision for the last change to the specified line.
     *
     * @param line line number (counting from 1)
     * @return revision string
     */
    public String getRevision(int line) {
        return lines.get(line-1).revision;
    }

    /**
     * Gets the author who last modified the specified line.
     *
     * @param line line number (counting from 1)
     * @return author
     */
    public String getAuthor(int line) {
        return lines.get(line-1).author;
    }

    /**
     * Returns the size of the file (number of lines).
     *
     * @return number of lines
     */
    public int size() {
        return lines.size();
    }

    /**
     * Returns the widest revision string in the file (used for pretty
     * printing).
     *
     * @return number of characters in the widest revision string
     */
    public int getWidestRevision() {
        return widestRevision;
    }

    /**
     * Returns the widest author name in the file (used for pretty printing).
     *
     * @return number of characters in the widest author string
     */
    public int getWidestAuthor() {
        return widestAuthor;
    }

    /**
     * Adds a line to the file.
     *
     * @param revision revision number
     * @param author author name
     */
    void addLine(String revision, String author) {
        lines.add(new Line(revision, author));
        widestRevision = Math.max(widestRevision, revision.length());
        widestAuthor = Math.max(widestAuthor, author.length());
    }

    /** Class representing one line in the file. */
    private static class Line {
        final String revision;
        final String author;
        Line(String rev, String aut) {
            revision = rev;
            author = aut;
        }
    }
}
