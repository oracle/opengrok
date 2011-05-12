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
import java.util.List;

/**
 * Class representing the history of a file.
 */
public class History {
    /** Entries in the log. The first entry is the most recent one. */
    private List<HistoryEntry> entries;

    public History() {
        this(new ArrayList<HistoryEntry>());
    }

    History(List<HistoryEntry> entries) {
        this.entries = entries;
    }

    /**
     * Set the list of log entries for the file. The first entry is the most
     * recent one.
     *
     * @param entries The entries to add to the list
     */
    public void setHistoryEntries(List<HistoryEntry> entries) {
        this.entries = entries;
    }

    /**
     * Get the list of log entries, most recent first.
     *
     * @return The list of entries in this history
     */
    public List<HistoryEntry> getHistoryEntries() {
        return entries;
    }

    /**
     * Check if at least one history entry has a file list.
     *
     * @return {@code true} if at least one of the entries has a non-empty
     * file list, {@code false} otherwise
     */
    public boolean hasFileList() {
        for (HistoryEntry entry : entries) {
            if (!entry.getFiles().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
