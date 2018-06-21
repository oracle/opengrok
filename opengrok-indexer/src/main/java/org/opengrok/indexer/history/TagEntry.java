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
 * Copyright (c) 2012, 2018 Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.util.Date;

/**
 * Class representing tag as Pair&lt;revision, tag string&gt;. Has overloaded
 * equals() using only revision string.
 *
 * @author Stanislav Kozina
 */
public abstract class TagEntry implements Comparable {

    protected int revision;
    /**
     * If repo uses linear revision numbering
     */
    protected Date date;
    /**
     * If repo does not use linear numbering
     */
    protected String tags;
    /**
     * Tag of the revision
     */

    protected static final int NOREV = -1;

    /**
     * Revision number not present
     */

    /**
     * Revision number not present
     *
     * @param revision revision number
     * @param tags string representing tags
     */
    public TagEntry(int revision, String tags) {
        this.revision = revision;
        this.date = null;
        this.tags = tags;
    }

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
     * @param aThat Compare to.
     * @return 1 for greater, 0 for equal and -1 for smaller objects.
     */
    @Override
    public int compareTo(Object aThat) {
        if (this == aThat) {
            return 0;
        }

        final TagEntry that = (TagEntry) aThat;

        if (this.revision != NOREV) {
            return ((Integer) this.revision).compareTo(that.revision);
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
