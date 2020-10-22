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
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.util.ArrayList;
import java.util.List;

/**
 * Class representing the history of a file.
 */
public class History {
    /** Entries in the log. The first entry is the most recent one. */
    private List<HistoryEntry> entries;
    /** 
     * track renamed files so they can be treated in special way (for some
     * SCMs) during cache creation.
     * These are relative to repository root.
     */
    private List<String> renamedFiles = new ArrayList<String>();
    
    public History() {
        this(new ArrayList<HistoryEntry>());
    }

    History(List<HistoryEntry> entries) {
        this.entries = entries;
    }

    History(List<HistoryEntry> entries, List<String> renamed) {
        this.entries = entries;
        this.renamedFiles = renamed;
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
     * Get the list of log entries, most recent first.
     * With parameters
     * @param limit max number of entries
     * @param offset starting position
     *
     * @return The list of entries in this history
     */
    public List<HistoryEntry> getHistoryEntries(int limit, int offset) {
        offset = offset < 0 ? 0 : offset;
        limit = offset + limit > entries.size() ? limit = entries.size() - offset : limit;
        return entries.subList(offset, offset + limit);
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

    /**
     * Check if at least one history entry has a tag list.
     *
     * @return {@code true} if at least one of the entries has a non-empty
     * tag list, {@code false} otherwise
     */
    public boolean hasTags() {
        // TODO Use a private variable instead of for loop?
        for (HistoryEntry entry : entries) {
            if (entry.getTags() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a value indicating if {@code file} is in the list of renamed files.
     * TODO: Warning -- this does a slow {@link List} search.
     */
    public boolean isRenamed(String file) {
        return renamedFiles.contains(file);
    }

    public List<String> getRenamedFiles() {
        return renamedFiles;
    }
}
