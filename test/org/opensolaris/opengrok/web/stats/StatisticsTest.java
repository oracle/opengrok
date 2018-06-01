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
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web.stats;

import org.apache.lucene.search.ScoreDoc;
import org.junit.Test;
import org.opensolaris.opengrok.web.SearchHelper;

import java.io.Reader;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Krystof Tulinger
 */
public class StatisticsTest {

    @Test
    public void encodeDecodeTest() {
        Statistics stats = new Statistics();

        stats.addRequest("/");
        stats.addRequest("/");

        String encoded = stats.encode();

        assertEquals(stats, Statistics.decode(encoded));
    }

    @Test
    public void testNewInstance() {
        Statistics stat = new Statistics();
        assertNotNull(stat.getCategoriesCounter());
        assertNotNull(stat.getTiming());
        assertNotNull(stat.getTimingMin());
        assertNotNull(stat.getTimingMax());
        assertNotNull(stat.getTimingAvg());

        assertEquals(0, stat.getRequests());
        assertEquals(1, stat.getMinutes()); // current minute
        assertEquals(0, stat.getRequestsPerMinute());
        assertEquals(0, stat.getRequestsPerMinuteMin());
        assertEquals(0, stat.getRequestsPerMinuteMax());
        assertEquals(0, stat.getRequestsPerMinuteAvg(), 0.0);
    }

    @Test
    public void testMaybeRefresh() throws Exception {
        Instant start = Instant.now();

        Duration[] durations = new Duration[] {
                Duration.ofMillis(5 * 65 * 1000),
                Duration.ofMillis(7 * 63 * 1000),
                Duration.ofMillis(3256 * 60 * 1001),
                Duration.ofMillis(13647987987L * 60 * 1000)
        };

        for (Duration duration : durations) {
            Statistics stat = new Statistics();
            assertEquals(1, stat.getMinutes());
            setTimeStart(stat, start.minus(duration));

            assertEquals(duration.toMinutes() + 1, stat.getMinutes());
        }
    }

    private void setTimeStart(final Statistics statistics, final Instant value) throws Exception {
        Field f = Statistics.class.getDeclaredField("timeStart");
        f.setAccessible(true);
        f.set(statistics, value);
    }

    @Test
    public void testAddRequestTimeCategory() {
        String[] testCategories = new String[]{
                "root", "/", "*", "!x.", "search", "xref"
        };
        for (String category : testCategories) {
            Statistics stat = new Statistics();
            stat.addTimingAndIncreaseCounter(category, Duration.ofMillis((long) (Math.random() * Long.MAX_VALUE)));
            assertEquals(1, stat.getCategoriesCounter().size());
            assertTrue(stat.getCategoriesCounter().containsKey(category));
            assertNotNull(stat.getCategoriesCounter().get(category));
            assertEquals(1, stat.getCategoriesCounter().get(category).longValue());
            assertEquals(1, stat.getTiming().size());
            assertEquals(1, stat.getTimingMin().size());
            assertEquals(1, stat.getTimingMax().size());
            assertEquals(1, stat.getTimingAvg().size());
            assertTrue(stat.getTiming().containsKey(category));
            assertTrue(stat.getTimingMin().containsKey(category));
            assertTrue(stat.getTimingMax().containsKey(category));
            assertTrue(stat.getTimingAvg().containsKey(category));
            assertNotNull(stat.getTiming().get(category));
            assertNotNull(stat.getTimingMin().get(category));
            assertNotNull(stat.getTimingMax().get(category));
            assertNotNull(stat.getTimingAvg().get(category));
        }
    }

    @Test
    public void testAddRequestTimeMultipleCategories() {
        Statistics stat = new Statistics();
        String[] testCategories = new String[]{
                "root", "/", "*", "/", "search", "/", "*", "/"
        };
        long[] testValues = new long[]{
                1, 1, 1, 2, 1, 3, 2, 4
        };

        int[] testSizes = new int[]{
                1, 2, 3, 3, 4, 4, 4, 4
        };

        assertEquals(testCategories.length, testValues.length);
        assertEquals(testCategories.length, testSizes.length);

        for (int i = 0; i < testCategories.length; i++) {
            stat.addTimingAndIncreaseCounter(testCategories[i], Duration.ofMillis((long) (Math.random() * Long.MAX_VALUE)));
            assertEquals(testSizes[i], stat.getCategoriesCounter().size());
            assertTrue(stat.getCategoriesCounter().containsKey(testCategories[i]));
            assertNotNull(stat.getCategoriesCounter().get(testCategories[i]));
            assertEquals(testValues[i], stat.getCategoriesCounter().get(testCategories[i]).longValue());
            assertEquals(testSizes[i], stat.getTiming().size());
            assertEquals(testSizes[i], stat.getTimingMin().size());
            assertEquals(testSizes[i], stat.getTimingMax().size());
            assertEquals(testSizes[i], stat.getTimingAvg().size());
            assertTrue(stat.getTiming().containsKey(testCategories[i]));
            assertTrue(stat.getTimingMin().containsKey(testCategories[i]));
            assertTrue(stat.getTimingMax().containsKey(testCategories[i]));
            assertTrue(stat.getTimingAvg().containsKey(testCategories[i]));
            assertNotNull(stat.getTiming().get(testCategories[i]));
            assertNotNull(stat.getTimingMin().get(testCategories[i]));
            assertNotNull(stat.getTimingMax().get(testCategories[i]));
            assertNotNull(stat.getTimingAvg().get(testCategories[i]));
        }
    }

    @Test
    public void testAddRequestTimeSum() {
        String[] testCategories = new String[]{
                "root", "/", "*", "!x.", "search", "xref"
        };
        long[] testValues = new long[]{
                10, 200, 300, -300, 100, 3641235546646L
        };

        assertEquals(testCategories.length, testValues.length);

        for (int i = 0; i < testCategories.length; i++) {
            Statistics stat = new Statistics();
            stat.addTiming(testCategories[i], Duration.ofMillis(testValues[i]));
            assertEquals(testValues[i], stat.getTiming().get(testCategories[i]).longValue());
        }
    }

    @Test
    public void testAddRequestMultipleTimeSum() {
        Statistics stat = new Statistics();
        String[] testCategories = new String[]{
                "root", "/", "root", "!x.", "/", "xref", "x"
        };
        long[] testValues = new long[]{
                10, 200, 300, -300, 100, 3641235546646L, 1
        };
        long[] testExpected = new long[]{
                10, 200, 310, -300, 300, 3641235546646L, 1
        };

        assertEquals(testCategories.length, testValues.length);
        assertEquals(testCategories.length, testExpected.length);

        for (int i = 0; i < testCategories.length; i++) {
            stat.addTiming(testCategories[i], Duration.ofMillis(testValues[i]));
            assertEquals(testExpected[i], stat.getTiming().get(testCategories[i]).longValue());
        }
    }

    @Test
    public void testSearchStatsHitsNull() {
        SearchHelper helper = new SearchHelper();
        helper.hits = null;
        helper.totalHits = 0;

        testSearchStats(helper, 1, 1, 0);
    }

    private static void testSearchStats(
            final SearchHelper searchHelper,
            final int expectedSearchRequests,
            final int expectedEmptySearchRequests,
            final int expectedAvgSearchHits
    ) {
        Statistics stats = new Statistics();

        stats.addRequest(Collections.singletonList("search"), Duration.ofMillis(100), searchHelper);

        assertEquals(expectedSearchRequests, stats.getCount("search"));
        assertEquals(expectedEmptySearchRequests, stats.getCount("empty_search"));
        assertEquals(expectedAvgSearchHits, stats.getAverageSearchHits());
    }

    @Test
    public void testSearchStatsHitsEmpty() {
        SearchHelper helper = new SearchHelper();
        helper.hits = new ScoreDoc[0];
        helper.totalHits = 0;

        testSearchStats(helper, 1, 1, 0);
    }

    @Test
    public void testSearchStats() {
        SearchHelper helper = new SearchHelper();
        helper.hits = Collections.nCopies(10, new ScoreDoc(1, 1)).toArray(new ScoreDoc[10]);
        helper.totalHits = helper.hits.length;

        testSearchStats(helper, 1, 0, 10);
    }

    @Test
    public void testSearchStatsAvg() {
        Statistics stats = new Statistics();

        SearchHelper helper = new SearchHelper();
        helper.hits = Collections.nCopies(10, new ScoreDoc(1, 1)).toArray(new ScoreDoc[10]);
        helper.totalHits = helper.hits.length;

        stats.addRequest(Collections.singletonList("search"), Duration.ofMillis(100), helper);

        helper.hits = Collections.nCopies(20, new ScoreDoc(1, 1)).toArray(new ScoreDoc[20]);
        helper.totalHits = helper.hits.length;

        stats.addRequest(Collections.singletonList("search"), Duration.ofMillis(100), helper);

        assertEquals(2, stats.getCount("search"));
        assertEquals(0, stats.getCount("empty_search"));
        assertEquals(15, stats.getAverageSearchHits());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDecodeString() {
        Statistics.decode((String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullDecodeReader() {
        Statistics.decode((Reader) null);
    }

}