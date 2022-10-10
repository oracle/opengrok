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
 */
package org.opengrok.indexer.history;

import java.io.Serializable;
import java.util.Objects;

/**
 * Class representing one line in the file.
 * <p>
 * The getters and setters are solely for (de)serialization.
 * Normally the respective members would be final.
 * </p>
 */
public class AnnotationLine implements Serializable {

    private static final long serialVersionUID = -1;

    private String revision;
    private String author;
    private boolean enabled;

    public AnnotationLine() {
        // for serialization
    }

    AnnotationLine(String revision, String author, boolean enabled) {
        this.revision = (revision == null) ? "" : revision;
        this.author = (author == null) ? "" : author;
        this.enabled = enabled;
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

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        AnnotationLine other = (AnnotationLine) obj;
        if (!Objects.equals(isEnabled(), other.isEnabled())) {
            return false;
        }
        if (!Objects.equals(getAuthor(), other.getAuthor())) {
            return false;
        }
        if (!Objects.equals(getRevision(), other.getRevision())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isEnabled(), getAuthor(), getRevision());
    }
}
