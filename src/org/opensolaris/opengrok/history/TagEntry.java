/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.util.Date;

/**
 * Class representing tag as Pair<revision, tag string>. Has overloaded equals()
 * using only revision string.
 *
 * @author Stanislav Kozina
 */
public abstract class TagEntry implements Comparable {

    protected int revision; /**< If repo uses linear revision numbering */
    protected Date date; /**< If repo does not use linear numbering */
    protected String tags; /**< Tag of the revision */
    
    protected static final int NOREV = -1; /**< Revision number not present */
    public TagEntry(int revision, String tags) {
        this.revision = revision;
        this.date = null;
        this.tags = tags;
    }

    public TagEntry(Date date, String tags) {
        if (date == null) {
            throw new NullPointerException("Can't create TagEntry using date==null");
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
