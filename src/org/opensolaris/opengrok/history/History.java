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

import java.util.List;

/**
 * Class representing the history of a file.
 */
public class History {
    /** Entries in the log. The first entry is the most recent one. */
    private List<HistoryEntry> entries;

    /**
     * List of the last log entry for the lines in the file. If a line does not
     * have a <code>LineInfo</code> object, its last update was at the same
     * revision as the previous line.
     */
    private List<LineInfo> annotation;

    /**
     * Set the list of log entries for the file. The first entry is the most
     * recent one.
     */
    public void setHistoryEntries(List<HistoryEntry> entries) {
        this.entries = entries;
    }

    /**
     * Get the list of log entries, most recent first.
     */
    public List<HistoryEntry> getHistoryEntries() {
        return entries;
    }

    /**
     * Set list of annotation/blame information. Each element describes the
     * last change of a line. If there is no entry for a line, the information
     * about the previous line applies (to save space when the object is stored
     * on disk).
     */
    public void setAnnotation(List<LineInfo> annotation) {
        this.annotation = annotation;
    }

    /**
     * Get the list of annotation/blame information. Each element describes the
     * last change of a line. If there is no entry for a line, the information
     * about the previous line applies (to save space when the object is stored
     * on disk).
     */
    public List<LineInfo> getAnnotation() {
        return annotation;
    }

    /**
     * Returns the <code>HistoryEntry</code> object for the specified line.
     */
    public HistoryEntry getEntryForLine(int line) {
        HistoryEntry prev = null;
        for (LineInfo li : annotation) {
            if (li.getLineNumber() > line) {
                return prev;
            }
            prev = li.getEntry();
        }
        return prev;
    }
}
