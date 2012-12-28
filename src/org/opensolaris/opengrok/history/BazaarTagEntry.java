/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

/**
 * Bazaar specific tag class with ability to compare itself
 * with generic HistoryEntry.
 * 
 * @author Stanislav Kozina
 */
public class BazaarTagEntry extends TagEntry {

    public BazaarTagEntry(int revision, String tag) {
        super(revision, tag);
    }
    
    @Override
    public int compareTo(HistoryEntry that) {
        assert this.revision != NOREV : "BazaarTagEntry created without tag specified.specified";
        return ((Integer)this.revision).compareTo(Integer.parseInt(that.getRevision()));
    }
}
