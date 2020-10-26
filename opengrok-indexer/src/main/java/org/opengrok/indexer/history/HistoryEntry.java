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
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.util.Date;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Collect all information of a given revision.
 *
 * @author Trond Norbye
 */
public class HistoryEntry {

    static final String TAGS_SEPARATOR = ", ";

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryEntry.class);

    private String revision;
    private Date date;
    private String author;
    private String tags;

    @SuppressWarnings("PMD.AvoidStringBufferField")
    private final StringBuffer message;

    private boolean active;
    private SortedSet<String> files;

    /** Creates a new instance of HistoryEntry. */
    public HistoryEntry() {
        message = new StringBuffer();
        files = new TreeSet<>();
    }
    
    /**
     * Copy constructor.
     * @param that HistoryEntry object
     */
    public HistoryEntry(HistoryEntry that) {
        this.revision = that.revision;
        this.date = that.date;
        this.author = that.author;
        this.tags = that.tags;
        this.message = that.message;
        this.active = that.active;
        this.files = that.files;
    }

    public HistoryEntry(String revision, Date date, String author,
            String tags, String message, boolean active) {
        this.revision = revision;
        setDate(date);
        this.author = author;
        this.tags = tags;
        this.message = new StringBuffer(message);
        this.active = active;
        this.files = new TreeSet<>();
    }

    public String getLine() {
        return revision + " " + date + " " + author + " " + message + "\n";
    }

    public void dump() {

        LOGGER.log(Level.FINE, "HistoryEntry : revision       = {0}", revision);
        LOGGER.log(Level.FINE, "HistoryEntry : tags           = {0}", tags);
        LOGGER.log(Level.FINE, "HistoryEntry : date           = {0}", date);
        LOGGER.log(Level.FINE, "HistoryEntry : author         = {0}", author);
        LOGGER.log(Level.FINE, "HistoryEntry : active         = {0}", (active ?
                "True" : "False"));
        String[] lines = message.toString().split("\n");
        String separator = "=";
        for (String line : lines) {
            LOGGER.log(Level.FINE, "HistoryEntry : message        {0} {1}",
                    new Object[]{separator, line});
            separator = ">";
        }
        separator = "=";
        for (String file : files) {
            LOGGER.log(Level.FINE, "HistoryEntry : files          {0} {1}",
                    new Object[]{separator, file});
            separator = ">";
        }
   }

    public String getAuthor() {
        return author;
    }
    
    public String getTags() {
        return tags;
    }

    public Date getDate() {
        return (date == null) ? null : (Date) date.clone();
    }

    public String getMessage() {
        return message.toString().trim();
    }

    public String getRevision() {
        return revision;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }

    public final void setDate(Date date) {
        if (date == null) {
            this.date = null;
        } else {
            this.date = (Date) date.clone();
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setMessage(String message) {
        this.message.setLength(0);
        this.message.append(message);
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public void appendMessage(String message) {
        this.message.append(message);
        this.message.append("\n");
    }

    public void addFile(String file) {
        files.add(file);
    }

    public SortedSet<String> getFiles() {
        return files;
    }

    public void setFiles(SortedSet<String> files) {
        this.files = files;
    }

    @Override
    public String toString() {
        return getLine();
    }

    /**
     * Remove list of files and tags.
     */
    public void strip() {
        stripFiles();
        stripTags();
    }

    /**
     * Remove list of files.
     */
    public void stripFiles() {
        files.clear();
    }

    /**
     * Remove tags.
     */
    public void stripTags() {
        tags = null;
    }
}
