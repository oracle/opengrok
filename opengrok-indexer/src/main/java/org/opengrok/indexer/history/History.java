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
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class representing the history of a file.
 */
public class History implements Serializable {

    private static final long serialVersionUID = -1;

    static final String TAGS_SEPARATOR = ", ";

    /** Entries in the log. The first entry is the most recent one. */
    private List<HistoryEntry> entries;
    /** 
     * track renamed files so they can be treated in special way (for some
     * SCMs) during cache creation.
     * These are relative to repository root.
     */
    private final Set<String> renamedFiles;

    // Needed for serialization
    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public void setEntries(List<HistoryEntry> entries) {
        this.entries = entries;
    }

    // TODO: use History entry identification (revision) as key ?
    private Map<String, String> tags = new HashMap<>();

    public History() {
        this(new ArrayList<>());
    }

    History(List<HistoryEntry> entries) {
        this(entries, Collections.emptyList());
    }

    History(List<HistoryEntry> entries, List<String> renamed) {
        this.entries = entries;
        this.renamedFiles = new HashSet<>(renamed);
    }

    History(List<HistoryEntry> entries, Set<String> renamed) {
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
        offset = Math.max(offset, 0);
        limit = offset + limit > entries.size() ? entries.size() - offset : limit;
        return entries.subList(offset, offset + limit);
    }

    /**
     * Check if at least one history entry has a file list.
     *
     * @return {@code true} if at least one of the entries has a non-empty
     * file list, {@code false} otherwise
     */
    public boolean hasFileList() {
        return entries.stream()
                .map(HistoryEntry::getFiles)
                .anyMatch(files -> !files.isEmpty());
    }

    /**
     * Check if at least one history entry has a tag list.
     *
     * @return {@code true} if at least one of the entries has a non-empty
     * tag list, {@code false} otherwise
     */
    public boolean hasTags() {
        return !tags.isEmpty();
    }

    public void addTags(HistoryEntry entry, String newTags) {
        tags.merge(entry.getRevision(), newTags, (a, b) -> a + TAGS_SEPARATOR + b);
    }

    /**
     * Gets a value indicating if {@code file} is in the list of renamed files.
     * @param file file path
     * @return is file renamed
     */
    public boolean isRenamed(String file) {
        return renamedFiles.contains(file);
    }

    public Set<String> getRenamedFiles() {
        return renamedFiles;
    }

    /**
     * Strip files and tags.
     * @see HistoryEntry#strip()
     */
    public void strip() {
        for (HistoryEntry ent : this.getHistoryEntries()) {
            ent.strip();
        }

        tags.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        History that = (History) o;
        return Objects.equals(this.getHistoryEntries(), that.getHistoryEntries()) &&
                Objects.equals(this.getTags(), that.getTags()) &&
                Objects.equals(this.getRenamedFiles(), that.getRenamedFiles());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHistoryEntries(), getTags(), getRenamedFiles());
    }

    @Override
    public String toString() {
        return this.getHistoryEntries().toString() + ", renamed files: " + this.getRenamedFiles().toString() +
                " , tags: " + getTags();
    }
}
