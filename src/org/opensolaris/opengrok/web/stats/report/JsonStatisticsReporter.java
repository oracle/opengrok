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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.opensolaris.opengrok.web.stats.Statistics;

import java.lang.reflect.Type;

public class JsonStatisticsReporter extends StatisticsReporter {

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Statistics.class, new CustomStatisticsSerializer())
            .create();

    @Override
    public String report(final Statistics stats) {
        if (stats == null) {
            throw new IllegalArgumentException("Cannot report null stats");
        }
        return gson.toJson(stats);
    }

    private static class CustomStatisticsSerializer implements JsonSerializer<Statistics> {

        @Override
        public JsonElement serialize(final Statistics stats, final Type type, final JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            if (stats.getRequests() == 0) {
                return obj;
            }

            obj.add(REQUEST_CATEGORIES, context.serialize(stats.getRequestCategories()));
            obj.add(TIMING, context.serialize(stats.getTiming()));
            obj.add(TIMING_MIN, context.serialize(stats.getTimingMin()));
            obj.add(TIMING_MAX, context.serialize(stats.getTimingMax()));
            obj.add(TIMING_AVG, context.serialize(stats.getTimingAvg()));
            obj.add(MINUTES, context.serialize(stats.getMinutes()));
            obj.add(REQUESTS, context.serialize(stats.getRequests()));
            obj.add(REQUESTS_PER_MINUTE, context.serialize(stats.getRequestsPerMinute()));
            obj.add(REQUESTS_PER_MINUTE_MIN, context.serialize(stats.getRequestsPerMinuteMin()));
            obj.add(REQUESTS_PER_MINUTE_MAX, context.serialize(stats.getRequestsPerMinuteMax()));
            obj.add(REQUESTS_PER_MINUTE_AVG, context.serialize(stats.getRequestsPerMinuteAvg()));
            obj.add(DAY_HISTOGRAM, context.serialize(stats.getDayHistogram()));
            obj.add(MONTH_HISTOGRAM, context.serialize(stats.getMonthHistogram()));
            obj.add(SEARCH_REQUESTS, context.serialize(stats.getSearchRequests()));
            obj.add(ZERO_HIT_SEARCH_COUNT, context.serialize(stats.getZeroHitSearchCount()));
            obj.add(AVERAGE_SEARCH_HITS, context.serialize(stats.getAverageSearchHits()));

            return obj;
        }
    }

}
