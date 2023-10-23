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
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2023, Ric Harris <harrisric@users.noreply.github.com>.
 */
package org.opengrok.indexer.history;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Class representing one line in the file.
 * <p>
 * The getters and setters are solely for (de)serialization.
 * Normally the respective members would be final.
 * </p>
 */
public class AnnotationLine implements Serializable {

    private static final long serialVersionUID = 3402729223485872826L;

    private String revision;
    private String author;
    private boolean enabled;

    private String displayRevision;

    public AnnotationLine() {
        // for serialization
    }

    /**
     * An annotation line where the revision is not overridden for display purposes.
     * @param revision revision string
     * @param author author string
     * @param enabled whether it is enabled
     */
    AnnotationLine(String revision, String author, boolean enabled) {
      this(revision, author, enabled, null);
    }

    AnnotationLine(String revision, String author, boolean enabled, String displayRevision) {
        this.revision = revision == null ? "" : revision;
        this.author = author == null ? "" : author;
        this.enabled = enabled;
        this.setDisplayRevision(displayRevision);
    }

    public String getRevision() {
        return revision;
    }

    public String getAuthor() {
        return author;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * A specialised revision for display purposes.
     *
     * @return the display revision if not null, otherwise the revision.
     */
    public String getDisplayRevision() {
      return displayRevision == null ? revision : displayRevision;
    }

    public void setDisplayRevision(String displayRevision) {
      this.displayRevision = displayRevision;
    }

    @Override
    public boolean equals(Object obj) {
        return Optional.ofNullable(obj)
                .filter(other -> getClass() == other.getClass())
                .map(AnnotationLine.class::cast)
                .filter(other -> Objects.equals(isEnabled(), other.isEnabled()))
                .filter(other -> Objects.equals(getAuthor(), other.getAuthor()))
                .filter(other -> Objects.equals(getRevision(), other.getRevision()))
                .filter(other -> Objects.equals(getDisplayRevision(), other.getDisplayRevision()))
                .isPresent();
    }

    @Override
    public int hashCode() {
        return Objects.hash(isEnabled(), getAuthor(), getRevision());
    }
}
