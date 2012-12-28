/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.util.Date;

/**
 * Git specific tag class with ability to compare itself with
 * generic HistoryEntry.
 * 
 * @author Stanislav Kozina
 */
public class GitTagEntry extends TagEntry {
    private String hash;
    
    public GitTagEntry(String hash, Date date, String tags) {
        super(date, tags);
        this.hash = hash;
    }
    
    @Override
    public int compareTo(HistoryEntry that) {
        assert this.date != null : "Git TagEntry created without date specified";
        return this.date.compareTo(that.getDate());
    }    
}
