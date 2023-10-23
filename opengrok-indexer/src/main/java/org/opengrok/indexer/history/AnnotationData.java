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
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2023, Ric Harris <harrisric@users.noreply.github.com>.
 */
package org.opengrok.indexer.history;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds serializable content for {@link Annotation}.
 */
@JsonPropertyOrder({"revision", "filename"})
public class AnnotationData implements Serializable {

    private static final long serialVersionUID = -1;

    // for serialization
    public AnnotationData() {
    }

    public AnnotationData(String filename) {
        this.filename = filename;
    }

    private List<AnnotationLine> annotationLines = new ArrayList<>();
    private int widestRevision;
    private int widestAuthor;
    private String filename;
    /**
     * The revision it was generated for is used for staleness check in {@link FileAnnotationCache#get(File, String)}.
     * Storing it in the filename would not work well ({@link org.opengrok.indexer.util.TandemPath}
     * shortening with very long filenames), on the other hand it is necessary to deserialize the object
     * to tell whether it is stale.
     */
    String revision;

    public List<AnnotationLine> getLines() {
        return annotationLines;
    }

    public void setLines(List<AnnotationLine> annotationLines) {
        this.annotationLines = annotationLines;
    }

    // For serialization.
    public void setWidestRevision(int widestRevision) {
        this.widestRevision = widestRevision;
    }

    // For serialization.
    public void setWidestAuthor(int widestAuthor) {
        this.widestAuthor = widestAuthor;
    }

    /**
     * Gets the author who last modified the specified line.
     *
     * @param line line number (counting from 1)
     * @return author, or an empty string if there is no information about the
     * specified line
     */
    public String getAuthor(int line) {
        try {
            return annotationLines.get(line - 1).getAuthor();
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }

    /**
     * Gets the revision for the last change to the specified line.
     *
     * @param line line number (counting from 1)
     * @return revision string, or an empty string if there is no information
     * about the specified line
     */
    public String getRevision(int line) {
        try {
            return annotationLines.get(line - 1).getRevision();
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }

    /**
     * Gets the representation of the revision to be used for display purposes, which may be abbreviated, for the last change to the specified line.
     *
     * @param line line number (counting from 1)
     * @return revision string, or an empty string if there is no information
     * about the specified line
     */
    public String getRevisionForDisplay(int line) {
        try {
            return annotationLines.get(line - 1).getDisplayRevision();
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }


    /**
     * Gets the enabled state for the last change to the specified line.
     *
     * @param line line number (counting from 1)
     * @return true if the xref for this revision is enabled, false otherwise
     */
    public boolean isEnabled(int line) {
        try {
            return annotationLines.get(line - 1).isEnabled();
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    /**
     * @return revision this annotation was generated for
     */
    public String getRevision() {
        return revision;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    /**
     * Returns the size of the file (number of lines).
     *
     * @return number of lines
     */
    public int size() {
        return annotationLines.size();
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
     * @param annotationLine {@link AnnotationLine} instance
     */
    void addLine(final AnnotationLine annotationLine) {
        annotationLines.add(annotationLine);
        widestRevision = Math.max(widestRevision, annotationLine.getDisplayRevision().length());
        widestAuthor = Math.max(widestAuthor, annotationLine.getAuthor().length());
    }

    /**
     * @param revision revision number
     * @param author author name
     * @param enabled whether the line is enabled
     * @param displayRevision a specialised revision number of display purposes. Can be null in which case the revision number will be used.
     * @see #addLine(AnnotationLine)
     */
    void addLine(String revision, String author, boolean enabled, String displayRevision) {
        final AnnotationLine annotationLine = new AnnotationLine(revision, author, enabled, displayRevision);
        addLine(annotationLine);
    }

    /**
     * Gets all revisions that are in use, first is the lowest one (sorted using natural order).
     *
     * @return set of all revisions for given file
     */
    public Set<String> getRevisions() {
        Set<String> ret = new HashSet<>();
        for (AnnotationLine ln : annotationLines) {
            ret.add(ln.getRevision());
        }
        return ret;
    }

    @TestOnly
    Set<String> getAuthors() {
        return annotationLines.stream().map(AnnotationLine::getAuthor).collect(Collectors.toSet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotationLines, filename);
    }

    @Override
    public boolean equals(Object obj) {
        return Optional.ofNullable(obj)
                .filter(other -> getClass() == other.getClass())
                .map(AnnotationData.class::cast)
                .filter(other -> Objects.deepEquals(getLines(), other.getLines()))
                .filter(other -> Objects.equals(getFilename(), other.getFilename()))
                .filter(other -> Objects.equals(getWidestAuthor(), other.getWidestAuthor()))
                .filter(other -> Objects.equals(getWidestRevision(), other.getWidestRevision()))
                .filter(other -> Objects.equals(getRevision(), other.getRevision()))
                .isPresent();
    }
}
