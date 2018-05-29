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
import java.util.Collections;
import java.util.List;
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

    private transient Instant timeStart = Instant.now();

    private Map<String, Long> categoriesCounter = new TreeMap<>();
    private Map<String, Long> timing = new TreeMap<>();
    private Map<String, Long> timingMin = new TreeMap<>();
    private Map<String, Long> timingMax = new TreeMap<>();
    private long[] dayHistogram = new long[24];
    private long[] monthHistogram = new long[31];
    private volatile long requests = 0;
    private volatile long minutes = 1;
    private volatile long requestsPerMinute = 0;
    private volatile long requestsPerMinuteMin = Long.MAX_VALUE;
    private volatile long requestsPerMinuteMax = Long.MIN_VALUE;

    private AtomicLong searchHitsAccumulator = new AtomicLong();

    private final transient Object lock = new Object();

    public void addRequest(final String category) {
        if (category == null) {
            return;
        }
        addRequest(Collections.singletonList(category));
    }

    public void addRequest(final List<String> categories) {
        addRequest(categories, null);
    }

    public void addRequest(final String category, final Duration processTime) {
        if (category == null) {
            return;
        }
        addRequest(Collections.singletonList(category), processTime);
    }

    public void addRequest(final List<String> categories, final Duration processTime) {
        addRequest(categories, processTime, null);
    }

    public void addRequest(final List<String> categories, final Duration processTime, final SearchHelper helper) {
        synchronized (lock) {
            if (categories == null || categories.isEmpty()) {
                return;
            }
            addRequest();
            for (String category : categories) {
                increaseCounter(category);
            }
            if (processTime != null && !processTime.isNegative()) {
                for (String category : categories) {
                    addTiming(category, processTime);
                }
            }
            if (helper != null) {
                addSearchRequest(helper, processTime);
            }
        }
    }

    /**
     * Adds a single request into all requests.
     */
    private void addRequest() {
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
    private void maybeRefresh() {
        synchronized (lock) {
            Instant now = Instant.now();

            if (timeStart.plus(1, ChronoUnit.MINUTES).isBefore(now)) {
                // several minutes have passed
                minutes += Duration.between(timeStart, now).toMinutes();
                timeStart = now;
                requestsPerMinute = 0;
            }
        }
    }

    public void addTiming(final String category, final Duration processTime) {
        synchronized (lock) {
            Long val = timing.computeIfAbsent(category, key -> 0L);
            Long min = timingMin.get(category);
            Long max = timingMax.get(category);

            long millis = processTime.toMillis();
            if (max == null || millis > max) {
                max = millis;
            }
            if (min == null || millis < min) {
                min = millis;
            }

            val += millis;

            timing.put(category, val);
            timingMin.put(category, min);
            timingMax.put(category, max);
        }
    }

    public void increaseCounter(final String category) {
        synchronized (lock) {
            categoriesCounter.merge(category, 1L, Long::sum);
        }
    }

    private void addSearchRequest(final SearchHelper helper, final Duration processTime) {
        if (helper == null) {
            return;
        }
        if (helper.hits == null || helper.hits.length == 0) { // empty search
            increaseCounter("empty_search");
            addTiming("empty_search", processTime);
        } else { // successful search
            searchHitsAccumulator.getAndUpdate(value -> value + helper.totalHits);
            increaseCounter("successful_search");
            addTiming("successful_search", processTime);
        }
    }

    public void addTimingAndIncreaseCounter(final List<String> categories, final Duration processTime) {
        synchronized (lock) {
            if (categories == null) {
                return;
            }
            for (String category : categories) {
                addTimingAndIncreaseCounter(category, processTime);
            }
        }
    }

    public void addTimingAndIncreaseCounter(final String category, final Duration processTime) {
        synchronized (lock) {
            if (category == null) {
                return;
            }
            addTiming(category, processTime);
            increaseCounter(category);
        }
    }

    /**
     * Get value of given counter
     * @param category category
     * @return Long value
     */
    public long getCount(final String category) {
        synchronized (lock) {
            Long val = categoriesCounter.get(category);
            if (val == null) {
                return 0;
            }
            return val;
        }
    }

    public Map<String, Long> getCategoriesCounter() {
        synchronized (lock) {
            return new TreeMap<>(categoriesCounter);
        }
    }

    public Map<String, Long> getTiming() {
        synchronized (lock) {
            return new TreeMap<>(timing);
        }
    }

    public Map<String, Long> getTimingMin() {
        synchronized (lock) {
            return new TreeMap<>(timingMin);
        }
    }

    public Map<String, Long> getTimingMax() {
        synchronized (lock) {
            return new TreeMap<>(timingMax);
        }
    }

    /**
     * Get timing average for all requests.
     *
     * @see #getCategoriesCounter()
     * @return map of averages for each category
     */
    public Map<String, Double> getTimingAvg() {
        Map<String, Double> timingAvg = new TreeMap<>();
        synchronized (lock) {
            for (Map.Entry<String, Long> entry : timing.entrySet()) {
                timingAvg.put(entry.getKey(), entry.getValue().doubleValue() / getCount(entry.getKey()));
            }
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
        synchronized (lock) {
            return Arrays.copyOf(dayHistogram, dayHistogram.length);
        }
    }

    public long[] getMonthHistogram() {
        synchronized (lock) {
            return Arrays.copyOf(monthHistogram, monthHistogram.length);
        }
    }

    public long getAverageSearchHits() {
        long searchRequestsCount = getCount("search");
        if (searchRequestsCount == 0) {
            return 0;
        }
        return searchHitsAccumulator.get() / searchRequestsCount;
    }

    public String encode() {
        synchronized (lock) {
            return gson.toJson(this);
        }
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
    public boolean equals(final Object o) {
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
                && Objects.equals(categoriesCounter, that.categoriesCounter)
                && Objects.equals(timing, that.timing)
                && Objects.equals(timingMin, that.timingMin)
                && Objects.equals(timingMax, that.timingMax)
                && Arrays.equals(dayHistogram, that.dayHistogram)
                && Arrays.equals(monthHistogram, that.monthHistogram)
                && searchHitsAccumulator.get() == that.searchHitsAccumulator.get();
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(categoriesCounter, timing, timingMin, timingMax, requests, minutes,
                requestsPerMinute, requestsPerMinuteMin, requestsPerMinuteMax, searchHitsAccumulator.get());
        result = 31 * result + Arrays.hashCode(dayHistogram);
        result = 31 * result + Arrays.hashCode(monthHistogram);
        return result;
    }
}
