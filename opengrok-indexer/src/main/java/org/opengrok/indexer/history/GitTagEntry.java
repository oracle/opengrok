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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.util.Date;

/**
 * Git specific tag class with ability to compare itself with generic
 * {@link HistoryEntry}.
 *
 * @author Stanislav Kozina
 */
class GitTagEntry extends TagEntry {

    private final String hash;

    GitTagEntry(String hash, Date date, String tags) {
        super(date, tags);
        this.hash = hash;
    }

    /**
     * Gets the immutable, initialized Git hash value.
     */
    String getHash() {
        return hash;
    }

    @Override
    public int compareTo(HistoryEntry that) {
        assert this.date != null : "Git TagEntry created without date specified";
        return this.date.compareTo(that.getDate());
    }
}
