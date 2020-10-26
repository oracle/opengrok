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
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.util.Date;

/**
 * Represents an identifier for a version control "commit" (also known as a
 * "changeset") where the version control system uses either monotonic, linear
 * revision numbering; or alternatively where OpenGrok uses "commit time" as a
 * proxy for ancestry.
 *
 * @author Stanislav Kozina
 */
public abstract class TagEntry implements Comparable<TagEntry> {

    /**
     * If repo uses linear revision numbering.
     */
    protected final int revision;
    /**
     * If repo does not use linear numbering.
     */
    protected final Date date;
    /**
     * Tag of the revision.
     */
    protected String tags;
    /**
     * Sentinel value for a repo that does not use linear numbering.
     */
    protected static final int NOREV = -1;

    /**
     * Initializes an instance for a repo where revision number is present.
     *
     * @param revision revision number
     * @param tags string representing tags
     */
    public TagEntry(int revision, String tags) {
        this.revision = revision;
        this.date = null;
        this.tags = tags;
    }

    /**
     * Initializes an instance for a repo where revision number is not present
     * and {@code date} is used instead.
     *
     * @param date revision date
     * @param tags string representing tags
     * @throws IllegalArgumentException if {@code date} is null
     */
    public TagEntry(Date date, String tags) {
        if (date == null) {
            throw new IllegalArgumentException("`date' is null");
        }
        this.revision = NOREV;
        this.date = date;
        this.tags = tags;
    }

    public String getTags() {
        return this.tags;
    }

    public void setTags(String tag) {
        this.tags = tag;
    }

    public Date getDate() {
        return date;
    }

    /**
     * Necessary Comparable method, used for sorting of TagEntries.
     *
     * @param that Compare to.
     * @return 1 for greater, 0 for equal and -1 for smaller objects.
     */
    @Override
    public int compareTo(TagEntry that) {
        if (this == that) {
            return 0;
        }

        if (this.revision != NOREV) {
            return Integer.compare(this.revision, that.revision);
        }
        assert this.date != null : "date == null";
        return this.date.compareTo(that.date);
    }

    @Override
    public boolean equals(Object aThat) {
        if (this == aThat) {
            return true;
        }
        if (!(aThat instanceof TagEntry)) {
            return false;
        }
        TagEntry that = (TagEntry) aThat;
        if (this.revision != NOREV) {
            return this.revision == that.revision;
        }
        assert this.date != null : "date == null";
        return this.date.equals(that.date);
    }

    @Override
    public int hashCode() {
        if (this.revision != NOREV) {
            return this.revision;
        }
        assert this.date != null : "date == null";
        return this.date.hashCode();
    }

    /**
     * Compares to given HistoryEntry, needed for history parsing. Depends on
     * specific repository revision format, therefore abstract.
     *
     * @param aThat HistoryEntry we compare to.
     * @return 1 for greater, 0 for equal and -1 for smaller objects.
     */
    public abstract int compareTo(HistoryEntry aThat);
}
