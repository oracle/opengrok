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

import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import org.opensolaris.opengrok.web.SearchHelper;

/**
 * Framework for statistics gathering. So far used only by the webapp.
 *
 * @author Krystof Tulinger
 */
public class Statistics {

    private static final Gson gson = new Gson();

    private Instant timeStart = Instant.now();

    private Map<String, Long> requestCategories = new TreeMap<>();
    private Map<String, Long> timing = new TreeMap<>();
    private Map<String, Long> timingMin = new TreeMap<>();
    private Map<String, Long> timingMax = new TreeMap<>();
    private long[] dayHistogram = new long[24];
    private long[] monthHistogram = new long[31];
    private long requests = 0;
    private long minutes = 1;
    private long requestsPerMinute = 0;
    private long requestsPerMinuteMin = Long.MAX_VALUE;
    private long requestsPerMinuteMax = Long.MIN_VALUE;

    private AtomicLong searchRequests = new AtomicLong();
    private AtomicLong zeroHitSearchCount = new AtomicLong();
    private AtomicLong searchHitsAccumulator = new AtomicLong();

    /**
     * Adds a single request into all requests.
     */
    public synchronized void addRequest() {
        maybeRefresh();

        requestsPerMinute++;
        requests++;

        if (requestsPerMinute > requestsPerMinuteMax) {
            requestsPerMinuteMax = requestsPerMinute;
        }
        if (requestsPerMinute < requestsPerMinuteMin) {
            requestsPerMinuteMin = requestsPerMinute;
        }

        dayHistogram[Calendar.getInstance().get(Calendar.HOUR_OF_DAY)]++;
        monthHistogram[Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1]++;
    }

    /**
     * Refreshes the last timestamp and number of minutes since start if needed.
     */
    protected synchronized void maybeRefresh() {
        Instant now = Instant.now();

        if (timeStart.plus(1, ChronoUnit.MINUTES).isBefore(now)) {
            // several minutes have passed
            minutes += Duration.between(timeStart, now).toMinutes();
            timeStart = now;
            requestsPerMinute = 0;
        }
    }

    /**
     * Adds a request into the category
     *
     * @param category category
     */
    public synchronized void addRequest(String category) {
        requestCategories.merge(category, 1L, (oldValue, value) -> oldValue + 1);
    }

    /**
     * Get value of given counter
     * @param category category
     * @return Long value
     */
    public synchronized Long getRequest(String category) {
        return requestCategories.get(category);
    }

    /**
     * Adds a request's process time into given category.
     *
     * @param category category
     * @param v time spent on processing this request
     */
    public synchronized void addRequestTime(String category, long v) {
        addRequest(category);
        Long val = timing.computeIfAbsent(category, key -> 0L);
        Long min = timingMin.get(category);
        Long max = timingMax.get(category);

        if (max == null || v > max) {
            max = v;
        }
        if (min == null || v < min) {
            min = v;
        }

        val += v;

        timing.put(category, val);
        timingMin.put(category, min);
        timingMax.put(category, max);
    }

    public Map<String, Long> getRequestCategories() {
        return requestCategories;
    }

    public Map<String, Long> getTiming() {
        return timing;
    }

    public Map<String, Long> getTimingMin() {
        return timingMin;
    }

    public Map<String, Long> getTimingMax() {
        return timingMax;
    }

    /**
     * Get timing average for all requests.
     *
     * @see #getRequestCategories()
     * @return map of averages for each category
     */
    public Map<String, Double> getTimingAvg() {
        Map<String, Double> timingAvg = new TreeMap<>();
        for (Map.Entry<String, Long> entry : timing.entrySet()) {
            timingAvg.put(entry.getKey(), entry.getValue().doubleValue()
                    / requestCategories.get(entry.getKey()));
        }
        return timingAvg;
    }

    public long getRequests() {
        return requests;
    }

    public long getMinutes() {
        maybeRefresh();
        return minutes;
    }

    public long getRequestsPerMinute() {
        maybeRefresh();
        return requestsPerMinute;
    }

    public long getRequestsPerMinuteMin() {
        if (getRequests() <= 0) {
            return 0;
        }
        return requestsPerMinuteMin;
    }

    public long getRequestsPerMinuteMax() {
        if (getRequests() <= 0) {
            return 0;
        }
        return requestsPerMinuteMax;
    }

    public double getRequestsPerMinuteAvg() {
        maybeRefresh();
        return requests / (double) minutes;
    }

    public long[] getDayHistogram() {
        return dayHistogram;
    }

    public long[] getMonthHistogram() {
        return monthHistogram;
    }

    public long getSearchRequests() {
        return searchRequests.get();
    }

    public long getZeroHitSearchCount() {
        return zeroHitSearchCount.get();
    }

    public long getAverageSearchHits() {
        long searchRequestsCount = getSearchRequests();
        if (searchRequestsCount == 0) {
            return 0;
        }
        return searchHitsAccumulator.get() / searchRequestsCount;
    }

    public void addSearchRequest(final SearchHelper helper, final long processTime) {
        if (helper == null) { // ignore
            return;
        }
        searchRequests.incrementAndGet();
        if (helper.hits == null || helper.hits.length == 0) { // empty search
            zeroHitSearchCount.incrementAndGet();
            addRequestTime("empty_search", processTime);
        } else { // successful search
            searchHitsAccumulator.getAndUpdate(value -> value + helper.hits.length);
            addRequestTime("successful_search", processTime);
        }
    }

    public String encode() {
        return gson.toJson(this);
    }

    public static Statistics decode(final String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Cannot decode statistics from null");
        }
        return gson.fromJson(encoded, Statistics.class);
    }

    public static Statistics decode(final Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("Cannot decode statistics from null");
        }
        return gson.fromJson(reader, Statistics.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Statistics that = (Statistics) o;
        return requests == that.requests
                && minutes == that.minutes
                && requestsPerMinute == that.requestsPerMinute
                && requestsPerMinuteMin == that.requestsPerMinuteMin
                && requestsPerMinuteMax == that.requestsPerMinuteMax
                && Objects.equals(timeStart, that.timeStart)
                && Objects.equals(requestCategories, that.requestCategories)
                && Objects.equals(timing, that.timing)
                && Objects.equals(timingMin, that.timingMin)
                && Objects.equals(timingMax, that.timingMax)
                && Arrays.equals(dayHistogram, that.dayHistogram)
                && Arrays.equals(monthHistogram, that.monthHistogram)
                && searchRequests.get() == that.searchRequests.get()
                && zeroHitSearchCount.get() == that.zeroHitSearchCount.get()
                && searchHitsAccumulator.get() == that.searchHitsAccumulator.get();
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(timeStart, requestCategories, timing, timingMin, timingMax, requests, minutes,
                requestsPerMinute, requestsPerMinuteMin, requestsPerMinuteMax, searchRequests.get(),
                zeroHitSearchCount.get(), searchHitsAccumulator.get());
        result = 31 * result + Arrays.hashCode(dayHistogram);
        result = 31 * result + Arrays.hashCode(monthHistogram);
        return result;
    }
}
