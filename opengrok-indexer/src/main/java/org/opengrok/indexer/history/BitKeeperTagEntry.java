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

package org.opengrok.indexer.history;

import java.util.Date;

/**
 * BitKeeper specific tag class with ability to compare itself with generic HistoryEntry.
 *
 * @author James Service  {@literal <jas2701@googlemail.com>}
 */
public class BitKeeperTagEntry extends TagEntry {

    protected String changeset;

    public BitKeeperTagEntry(String changeset, Date date, String tag) {
        super(date, tag);
        this.changeset = changeset;
    }

    @Override
    public int compareTo(HistoryEntry that) {
        assert this.date != null : "BitKeeper TagEntry created without date specified";
        return this.date.compareTo(that.getDate());
    }
}
