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
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Collect all information of a given revision.
 *
 * @author Trond Norbye
 */
public class HistoryEntry implements Serializable {

    private static final long serialVersionUID = 1277313126047397131L;

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryEntry.class);

    private String revision;
    private String displayRevision;
    private Date date;
    private String author;

    @SuppressWarnings("PMD.AvoidStringBufferField")
    private final StringBuffer message;

    private boolean active;
    @JsonIgnore
    @SuppressWarnings("serial")
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
        this(that.revision, that.displayRevision, that.date, that.author, that.message.toString(), that.active, that.files);
    }

    public HistoryEntry(String revision, String displayRevision, Date date, String author, String message, boolean active, Collection<String> files) {
        this.revision = revision;
        this.displayRevision = displayRevision;
        setDate(date);
        this.author = author;
        this.message = new StringBuffer(message);
        this.active = active;
        this.files = new TreeSet<>();
        if (files != null) {
          this.files.addAll(files);
        }
    }

    public HistoryEntry(String revision, Date date, String author, String message, boolean active) {
        this(revision, null, date, author, message, active, null);
    }

    @VisibleForTesting
    HistoryEntry(String revision) {
        this();
        this.revision = revision;
    }

    @JsonIgnore
    public String getLine() {
        return String.join(" ",
                getRevision(), getDate().toString(), getAuthor(), message, "\n");
    }

    public void dump() {

        LOGGER.log(Level.FINE, "HistoryEntry : revision        = {0}", revision);
        LOGGER.log(Level.FINE, "HistoryEntry : displayRevision = {0}", displayRevision);
        LOGGER.log(Level.FINE, "HistoryEntry : date            = {0}", date);
        LOGGER.log(Level.FINE, "HistoryEntry : author          = {0}", author);
        LOGGER.log(Level.FINE, "HistoryEntry : active          = {0}", active ?
                "True" : "False");
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

    /**
     * @return description of selected fields; used in the web app.
     */
    @JsonIgnore
    public String getDescription() {
        return "changeset: " + getRevision()
                + "\nsummary: " + getMessage() + "\nuser: "
                + getAuthor() + "\ndate: " + getDate();
    }

    public String getAuthor() {
        return author;
    }

    public Date getDate() {
        return date == null ? null : (Date) date.clone();
    }

    public String getMessage() {
        return message.toString().trim();
    }

    public String getRevision() {
        return revision;
    }

    public String getDisplayRevision() {
      return displayRevision == null ? revision : displayRevision;
  }

    public void setAuthor(String author) {
        this.author = author;
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

    public void setDisplayRevision(String displayRevision) {
        this.displayRevision = displayRevision;
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

    /**
     * @deprecated The method is kept only for backward compatibility to avoid warnings when deserializing objects
     * from the previous format.
     * The tags were moved to the {@link History} class.
     * Will be removed sometime after the OpenGrok 1.8.0 version.
     */
    @Deprecated(since = "1.7.11", forRemoval = true)
    public void setTags(String tags) {
        // Tags moved to the History object.
    }

    @Override
    public String toString() {
        return String.join(" ",
                getRevision(), getDisplayRevision(), getDate().toString(), getAuthor(), getMessage(), getFiles().toString());
    }

    /**
     * Remove list of files and tags.
     */
    public void strip() {
        stripFiles();
    }

    /**
     * Remove list of files.
     */
    public void stripFiles() {
        files.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HistoryEntry that = (HistoryEntry) o;

        return Objects.equals(this.getAuthor(), that.getAuthor()) &&
                Objects.equals(this.getRevision(), that.getRevision()) &&
                Objects.equals(this.getDisplayRevision(), that.getDisplayRevision()) &&
                Objects.equals(this.getDate(), that.getDate()) &&
                Objects.equals(this.getMessage(), that.getMessage()) &&
                Objects.equals(this.getFiles(), that.getFiles());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAuthor(), getRevision(), getDisplayRevision(), getDate(), getMessage(), getFiles());
    }
}
