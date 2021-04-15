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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class HistoryTest {
    private final List<HistoryEntry> entries = List.of(
            new HistoryEntry("84599b3c", new Date(1485438707000L),
                    "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                    "    renaming directories\n\n", true,
                    Set.of("/git/moved2/renamed2.c")),
            new HistoryEntry("67dfbe26", new Date(1485263397000L),
                    "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                    "    renaming renamed -> renamed2\n\n", true,
                    Set.of("/git/moved/renamed2.c")));

    @Test
    public void testEqualsRenamed() {
        History history = new History(entries, List.of("moved/renamed2.c", "moved2/renamed2.c", "moved/renamed.c"));
        History historyNoRenames = new History(entries);
        assertNotEquals(history, historyNoRenames);
    }

    @Test
    public void testEquals() {
        History history = new History(entries);
        assertEquals(2, history.getHistoryEntries().size());
        History historySmaller = new History(entries.subList(1, 2));
        assertEquals(1, historySmaller.getHistoryEntries().size());
        assertNotEquals(history, historySmaller);
    }
}
