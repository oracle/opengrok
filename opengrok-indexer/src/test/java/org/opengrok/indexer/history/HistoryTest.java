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

import java.io.File;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HistoryTest {
    private final List<HistoryEntry> entries = List.of(
            new HistoryEntry("84599b3c", new Date(1485438707000L),
                    "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                    "    renaming directories\n\n", true,
                    Set.of(File.separator + Paths.get("git", "moved2", "renamed2.c"))),
            new HistoryEntry("67dfbe26", new Date(1485263397000L),
                    "Kryštof Tulinger <krystof.tulinger@oracle.com>", null,
                    "    renaming renamed -> renamed2\n\n", true,
                    Set.of(File.separator + Paths.get("git", "moved", "renamed2.c"))));

    @Test
    public void testEqualsRenamed() {
        History history = new History(entries,
                List.of(Paths.get("moved", "renamed2.c").toString(),
                        Paths.get("moved2", "renamed2.c").toString(),
                        Paths.get("moved", "renamed.c").toString()));
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

    @Test
    void testAddTags() {
        History history = new History();
        HistoryEntry historyEntry = entries.get(0);
        history.addTags(historyEntry, "foo");
        assertEquals(history.getTags().get(historyEntry.getRevision()), "foo");
        history.addTags(historyEntry, "bar");
        assertEquals(history.getTags().get(historyEntry.getRevision()), "foo" + History.TAGS_SEPARATOR + "bar");
    }

    @Test
    void testGetSetTags() {
        History history = new History();
        assertTrue(history.getTags().isEmpty());
        Map<String, String> tags = new TreeMap<>();
        tags.put("foo", "bar");
        tags.put("bar", "foo");
        history.setTags(tags);
        assertFalse(history.getTags().isEmpty());
        assertEquals(tags, history.getTags());
    }

    @Test
    void testEqualsTagsEmpty() {
        History history1 = new History();
        History history2 = new History();
        assertTrue(history1.getTags().isEmpty());
        assertTrue(history2.getTags().isEmpty());
        assertEquals(history1, history2);
    }

    @Test
    void testEqualsTagsPositive() {
        History history1 = new History();
        History history2 = new History();
        history1.setTags(Map.of("foo", "bar", "bar", "foo"));
        history2.setTags(Map.of("foo", "bar", "bar", "foo"));
        assertEquals(history1, history2);
    }

    @Test
    void testEqualsTagsNegative() {
        History history1 = new History();
        History history2 = new History();
        history1.setTags(Map.of("foo", "bar", "Bar", "foo"));
        history2.setTags(Map.of("foo", "bar", "bar", "foo"));
        assertNotEquals(history1, history2);
    }
}
