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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opengrok.indexer.analysis.AccumulatedNumLinesLOC;
import org.opengrok.indexer.analysis.NumLinesLOC;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NumLinesLOCAggregatorTest {

    @Test
    public void shouldEnumerateToRoot() {
        NumLinesLOCAggregator aggtor = new NumLinesLOCAggregator();
        final String PATH = "/a/b/c/f0";
        aggtor.register(new NumLinesLOC(PATH, 2, 1));
        List<AccumulatedNumLinesLOC> counts = new ArrayList<>();
        aggtor.iterator().forEachRemaining(counts::add);
        counts.sort(Comparator.comparingInt(o -> o.getPath().length()));

        assertEquals("agg count", 4, counts.size());

        AccumulatedNumLinesLOC entry = counts.get(0);
        assertEquals("counts[0] path", "/", entry.getPath());
        assertEquals("counts[0] numLines", 2, entry.getNumLines());
        assertEquals("counts[0] LOC", 1, entry.getLOC());

        entry = counts.get(1);
        assertEquals("counts[1] path", "/a", entry.getPath());
        assertEquals("counts[1] numLines", 2, entry.getNumLines());
        assertEquals("counts[1] LOC", 1, entry.getLOC());

        entry = counts.get(2);
        assertEquals("counts[2] path", "/a/b", entry.getPath());
        assertEquals("counts[2] numLines", 2, entry.getNumLines());
        assertEquals("counts[2] LOC", 1, entry.getLOC());

        entry = counts.get(3);
        assertEquals("counts[2] path", "/a/b/c", entry.getPath());
        assertEquals("counts[2] numLines", 2, entry.getNumLines());
        assertEquals("counts[2] LOC", 1, entry.getLOC());
    }

    @Test
    public void shouldAggregateToRoot() {
        NumLinesLOCAggregator aggtor = new NumLinesLOCAggregator();
        aggtor.register(new NumLinesLOC("/a/b/f0", 2, 1));
        aggtor.register(new NumLinesLOC("/a/c/f1", 5, 3));
        aggtor.register(new NumLinesLOC("/a/f2", 11, 7));
        List<AccumulatedNumLinesLOC> counts = new ArrayList<>();
        aggtor.iterator().forEachRemaining(counts::add);
        counts.sort(Comparator.comparingInt((AccumulatedNumLinesLOC o) ->
                o.getPath().length()).thenComparing(AccumulatedNumLinesLOC::getPath));

        assertEquals("agg count", 4, counts.size());

        AccumulatedNumLinesLOC entry = counts.get(0);
        assertEquals("counts[0] path", "/", entry.getPath());
        assertEquals("counts[0] numLines", 18, entry.getNumLines());
        assertEquals("counts[0] LOC", 11, entry.getLOC());

        entry = counts.get(1);
        assertEquals("counts[1] path", "/a", entry.getPath());
        assertEquals("counts[1] numLines", 18, entry.getNumLines());
        assertEquals("counts[1] LOC", 11, entry.getLOC());

        entry = counts.get(2);
        assertEquals("counts[2] path", "/a/b", entry.getPath());
        assertEquals("counts[2] numLines", 2, entry.getNumLines());
        assertEquals("counts[2] LOC", 1, entry.getLOC());

        entry = counts.get(3);
        assertEquals("counts[2] path", "/a/c", entry.getPath());
        assertEquals("counts[2] numLines", 5, entry.getNumLines());
        assertEquals("counts[2] LOC", 3, entry.getLOC());
    }
}
