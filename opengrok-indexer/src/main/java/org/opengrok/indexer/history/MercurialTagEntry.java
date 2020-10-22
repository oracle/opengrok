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
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

/**
 * Mercurial specific tag class with ability to compare itself with generic
 * HistoryEntry.
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
        String[] revs = that.getRevision().split(":");
        assert revs.length == 2 : "Unable to parse revision format";
        return ((Integer) this.revision).compareTo(Integer.parseInt(revs[0]));
    }
}
