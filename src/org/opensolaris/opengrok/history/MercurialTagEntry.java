/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

/**
 * Mercurial specific tag class with ability to compare itself with
 * generic HistoryEntry.
 * 
 * @author Stanislav Kozina
 */
public class MercurialTagEntry extends TagEntry {

    public MercurialTagEntry(int revision, String tag) {
        super(revision, tag);
    }    
        
    @Override
    public int compareTo(HistoryEntry that) {
        assert this.revision != NOREV : "Mercurial TagEntry created without revision specified";
        String revs[] = that.getRevision().split(":");
        assert revs.length == 2 : "Unable to parse revision format";
        return ((Integer)this.revision).compareTo(Integer.parseInt(revs[0]));
    }
}
