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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web.stats.report;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.lucene.search.ScoreDoc;
import org.junit.Test;
import org.opensolaris.opengrok.web.SearchHelper;
import org.opensolaris.opengrok.web.stats.Statistics;

import java.time.Duration;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JsonStatisticsReporterTest {

    private final JsonStatisticsReporter reporter = new JsonStatisticsReporter();

    @Test
    public void emptyEncodeTest() {
        Statistics stats = new Statistics();
        String report = reporter.report(stats);

        assertEquals("{}", report);
    }

    @Test
    public void requestEncodeTest() {
        Statistics stats = new Statistics();
        stats.addRequest("/");

        String report = reporter.report(stats);

        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(report).getAsJsonObject();

        assertEquals(1, json.get(StatisticsReporter.REQUESTS).getAsInt());
    }

    @Test
    public void categoryEncodeTest() {
        Statistics stats = new Statistics();
        stats.addRequest("/", Duration.ofMillis(100));

        String report = reporter.report(stats);

        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(report).getAsJsonObject();

        assertEquals(1, json.get(StatisticsReporter.CATEGORIES_COUNTER).getAsJsonObject().get("/").getAsInt());
    }

    @Test
    public void searchStatsTest() {
        Statistics stats = new Statistics();

        SearchHelper helper = new SearchHelper();
        helper.hits = new ScoreDoc[] {new ScoreDoc(1, 1)};
        helper.totalHits = helper.hits.length;

        stats.addRequest(Collections.singletonList("search"), Duration.ofMillis(100), helper);

        String report = reporter.report(stats);

        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(report).getAsJsonObject();

        assertEquals(1, json.get(StatisticsReporter.CATEGORIES_COUNTER).getAsJsonObject().get("search").getAsInt());
        assertNull(json.get(StatisticsReporter.CATEGORIES_COUNTER).getAsJsonObject().get("empty_search"));
        assertEquals(1, json.get(StatisticsReporter.AVERAGE_SEARCH_HITS).getAsInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNull() {
        reporter.report(null);
    }

}
