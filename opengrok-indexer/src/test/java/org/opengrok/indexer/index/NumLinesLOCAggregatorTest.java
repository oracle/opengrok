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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import org.junit.jupiter.api.Test;

import org.opengrok.indexer.analysis.AccumulatedNumLinesLOC;
import org.opengrok.indexer.analysis.NumLinesLOC;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumLinesLOCAggregatorTest {

    @Test
    void shouldEnumerateToRoot() {
        NumLinesLOCAggregator aggtor = new NumLinesLOCAggregator();
        final String PATH = "/a/b/c/f0";
        aggtor.register(new NumLinesLOC(PATH, 2, 1));
        List<AccumulatedNumLinesLOC> counts = new ArrayList<>();
        aggtor.iterator().forEachRemaining(counts::add);
        counts.sort(Comparator.comparingInt(o -> o.getPath().length()));

        assertEquals(4, counts.size(), "agg count");

        AccumulatedNumLinesLOC entry = counts.get(0);
        assertEquals("/", entry.getPath(), "counts[0] path");
        assertEquals(2, entry.getNumLines(), "counts[0] numLines");
        assertEquals(1, entry.getLOC(), "counts[0] LOC");

        entry = counts.get(1);
        assertEquals("/a", entry.getPath(), "counts[1] path");
        assertEquals(2, entry.getNumLines(), "counts[1] numLines");
        assertEquals(1, entry.getLOC(), "counts[1] LOC");

        entry = counts.get(2);
        assertEquals("/a/b", entry.getPath(), "counts[2] path");
        assertEquals(2, entry.getNumLines(), "counts[2] numLines");
        assertEquals(1, entry.getLOC(), "counts[2] LOC");

        entry = counts.get(3);
        assertEquals("/a/b/c", entry.getPath(), "counts[2] path");
        assertEquals(2, entry.getNumLines(), "counts[2] numLines");
        assertEquals(1, entry.getLOC(), "counts[2] LOC");
    }

    @Test
    void shouldAggregateToRoot() {
        NumLinesLOCAggregator aggtor = new NumLinesLOCAggregator();
        aggtor.register(new NumLinesLOC("/a/b/f0", 2, 1));
        aggtor.register(new NumLinesLOC("/a/c/f1", 5, 3));
        aggtor.register(new NumLinesLOC("/a/f2", 11, 7));
        List<AccumulatedNumLinesLOC> counts = new ArrayList<>();
        aggtor.iterator().forEachRemaining(counts::add);
        counts.sort(Comparator.comparingInt((AccumulatedNumLinesLOC o) ->
                o.getPath().length()).thenComparing(AccumulatedNumLinesLOC::getPath));

        assertEquals(4, counts.size(), "agg count");

        AccumulatedNumLinesLOC entry = counts.get(0);
        assertEquals("/", entry.getPath(), "counts[0] path");
        assertEquals(18, entry.getNumLines(), "counts[0] numLines");
        assertEquals(11, entry.getLOC(), "counts[0] LOC");

        entry = counts.get(1);
        assertEquals("/a", entry.getPath(), "counts[1] path");
        assertEquals(18, entry.getNumLines(), "counts[1] numLines");
        assertEquals(11, entry.getLOC(), "counts[1] LOC");

        entry = counts.get(2);
        assertEquals("/a/b", entry.getPath(), "counts[2] path");
        assertEquals(2, entry.getNumLines(), "counts[2] numLines");
        assertEquals(1, entry.getLOC(), "counts[2] LOC");

        entry = counts.get(3);
        assertEquals("/a/c", entry.getPath(), "counts[2] path");
        assertEquals(5, entry.getNumLines(), "counts[2] numLines");
        assertEquals(3, entry.getLOC(), "counts[2] LOC");
    }
}
