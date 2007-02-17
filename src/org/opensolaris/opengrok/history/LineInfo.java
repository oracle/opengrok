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
    /** The first line for which this information is valid. */
    private int lineNo;
    /** History entry describing the most recent update of the line. */
    private HistoryEntry entry;

    /**
     * Create an empty object.
     */
    public LineInfo() { }

    /**
     * Create a <code>LineInfo</code> object for a specified line.
     *
     * @param line line number
     * @param e most recent history entry for this line
     */
    public LineInfo(int line, HistoryEntry e) {
        lineNo = line;
        entry = e;
    }

    /**
     * Set the line number this information is valid for.
     */
    public void setLineNumber(int line) {
        lineNo = line;
    }

    /**
     * Get the line number this information is valid for.
     */
    public int getLineNumber() {
        return lineNo;
    }

    /**
     * Set the most recent history entry for this line.
     */
    public void setEntry(HistoryEntry e) {
        entry = e;
    }

    /**
     * Get the most recent history entry for this line.
     */
    public HistoryEntry getEntry() {
        return entry;
    }
}
