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
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Framework for statistics gathering. So far used only by the webapp.
 *
 * @author Krystof Tulinger
 */
public class Statistics {

    protected static final String STATISTIC_TIMING = "timing";
    protected static final String STATISTIC_TIMING_MIN = "timing_min";
    protected static final String STATISTIC_TIMING_MAX = "timing_max";
    protected static final String STATISTIC_TIMING_AVG = "timing_avg";
    protected static final String STATISTIC_REQUEST_CATEGORIES = "request_categories";
    protected static final String STATISTIC_REQUESTS = "requests";
    protected static final String STATISTIC_MINUTES = "minutes";
    protected static final String STATISTIC_REQUESTS_PER_MINUTE = "requests_per_minute";
    protected static final String STATISTIC_REQUESTS_PER_MINUTE_MIN = "requests_per_minute_min";
    protected static final String STATISTIC_REQUESTS_PER_MINUTE_MAX = "requests_per_minute_max";
    protected static final String STATISTIC_REQUESTS_PER_MINUTE_AVG = "requests_per_minute_avg";
    protected static final String STATISTIC_DAY_HISTOGRAM = "day_histogram";
    protected static final String STATISTIC_MONTH_HISTOGRAM = "month_histogram";

    @JsonProperty(STATISTIC_REQUEST_CATEGORIES)
    private Map<String, Long> requestCategories = new TreeMap<>();
    @JsonProperty(STATISTIC_TIMING)
    private Map<String, Long> timing = new TreeMap<>();
    @JsonProperty(STATISTIC_TIMING_MIN)
    private Map<String, Long> timingMin = new TreeMap<>();
    @JsonProperty(STATISTIC_TIMING_MAX)
    private Map<String, Long> timingMax = new TreeMap<>();
    @JsonProperty(STATISTIC_TIMING_AVG)
    private Map<String, Double> timingAvg = new TreeMap<>();
    @JsonProperty(STATISTIC_DAY_HISTOGRAM)
    private long[] dayHistogram = new long[24];
    @JsonProperty(STATISTIC_MONTH_HISTOGRAM)
    private long[] monthHistogram = new long[31];
    @JsonProperty(STATISTIC_REQUESTS)
    private long requests = 0;
    @JsonProperty(STATISTIC_MINUTES)
    private long minutes = 1;
    @JsonProperty(STATISTIC_REQUESTS_PER_MINUTE)
    private long requestsPerMinute = 0;
    @JsonProperty(STATISTIC_REQUESTS_PER_MINUTE_MIN)
    private long requestsPerMinuteMin = Long.MAX_VALUE;
    @JsonProperty(STATISTIC_REQUESTS_PER_MINUTE_MAX)
    private long requestsPerMinuteMax = Long.MIN_VALUE;
    @JsonProperty(STATISTIC_REQUESTS_PER_MINUTE_AVG)
    private double requestsPerMinuteAvg = 0;

    @JsonIgnore
    private long timeStart = System.currentTimeMillis();

    /**
     * Adds a single request into all requests.
     */
    public synchronized void addRequest() {
        maybeRefresh();

        requestsPerMinute++;
        requests++;
        requestsPerMinuteAvg = requests / (double) minutes;

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
        if (timeStart + 60 * 1000 <= System.currentTimeMillis()) {
            // several minutes have passed
            minutes += (System.currentTimeMillis() - timeStart) / (60 * 1000);
            timeStart = System.currentTimeMillis();
            requestsPerMinute = 0;
        }
    }

    /**
     * Adds a request into the category.
     *
     * @param category category
     */
    public synchronized void addRequest(String category) {
        maybeRefresh();
        Long val = requestCategories.get(category);
        if (val == null) {
            val = 0L;
        }
        val += 1;
        requestCategories.put(category, val);
    }
    
    /**
     * Get value of given counter.
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
        Long val = timing.get(category);
        Long min = timingMin.get(category);
        Long max = timingMax.get(category);

        if (val == null) {
            val = 0L;
        }
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

        // Recompute the average for given category.
        timingAvg.put(category,
                timing.get(category).doubleValue() / requestCategories.get(category));
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
        return timingAvg;
    }

    public synchronized void setRequestCategories(Map<String, Long> requestCategories) {
        this.requestCategories = requestCategories;
    }

    public synchronized void setTiming(Map<String, Long> timing) {
        this.timing = timing;
    }

    public synchronized void setTimingMin(Map<String, Long> timing_min) {
        this.timingMin = timing_min;
    }

    public synchronized void setTimingMax(Map<String, Long> timing_max) {
        this.timingMax = timing_max;
    }

    public synchronized void setTimingAvg(Map<String, Double> timing_avg) {
        this.timingAvg = timing_avg;
    }

    public long getTimeStart() {
        return timeStart;
    }

    public synchronized void setTimeStart(long timeStart) {
        this.timeStart = timeStart;
    }

    public long getRequests() {
        return requests;
    }

    public synchronized void setRequests(long requests) {
        this.requests = requests;
    }

    public long getMinutes() {
        return minutes;
    }

    public synchronized void setMinutes(long minutes) {
        this.minutes = minutes;
    }

    public long getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public synchronized void setRequestsPerMinute(long requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public long getRequestsPerMinuteMin() {
        if (getRequests() <= 0) {
            return 0;
        }
        return requestsPerMinuteMin;
    }

    public synchronized void setRequestsPerMinuteMin(long requestsPerMinuteMin) {
        this.requestsPerMinuteMin = requestsPerMinuteMin;
    }

    public long getRequestsPerMinuteMax() {
        if (getRequests() <= 0) {
            return 0;
        }
        return requestsPerMinuteMax;
    }

    public synchronized void setRequestsPerMinuteMax(long requestsPerMinuteMax) {
        this.requestsPerMinuteMax = requestsPerMinuteMax;
    }

    public double getRequestsPerMinuteAvg() {
        return this.requestsPerMinuteAvg;
    }

    public synchronized void setRequestsPerMinuteAvg(double value) {
        this.requestsPerMinuteAvg = value;
    }

    public long[] getDayHistogram() {
        return dayHistogram;
    }

    public synchronized void setDayHistogram(long[] dayHistogram) {
        this.dayHistogram = dayHistogram;
    }

    public long[] getMonthHistogram() {
        return monthHistogram;
    }

    public synchronized void setMonthHistogram(long[] monthHistogram) {
        this.monthHistogram = monthHistogram;
    }

    /**
     * Convert this {@code Statistics} object into JSON.
     *
     * @return the JSON string
     * @throws JsonProcessingException JSON processing exception
     */
    public String toJson() throws JsonProcessingException {
        return toJson(this);
    }

    /**
     * Convert JSON into {@code Statistics} object.
     *
     * @param jsonString String with JSON
     * @return the {@code Statistics} object
     * @throws IOException I/O exception
     */
    @SuppressWarnings("unchecked")
    public static Statistics fromJson(String jsonString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Statistics stats = mapper.readValue(jsonString, Statistics.class);
        stats.setTimeStart(System.currentTimeMillis());

        return stats;
    }

    /**
     * Convert statistics object into JSON.
     *
     * @param stats the statistics object
     * @return String with JSON
     * @throws JsonProcessingException JSON processing exception
     */
    @SuppressWarnings("unchecked")
    public static String toJson(Statistics stats) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        return mapper.writeValueAsString(stats);
    }

    @Override
    public String toString() {
        return "requestCategories = " + getRequestCategories().toString()
                + "\ntiming = " + getTiming().toString()
                + "\nntimingMin = " + getTimingMin().toString()
                + "\nntimingMax = " + getTimingMax().toString()
                + "\nntimingAvg = " + getTimingAvg().toString()
                + "\nminutes = " + getMinutes()
                + "\nrequests = " + getRequests()
                + "\nrequestsPerMinute = " + getRequestsPerMinute()
                + "\nrequestsPerMinuteMin = " + getRequestsPerMinuteMin()
                + "\nrequestsPerMinuteMax = " + getRequestsPerMinuteMax()
                + "\nrequestsPerMinuteAvg = " + getRequestsPerMinuteAvg()
                + "\ndayHistogram = " + LongStream.of(getDayHistogram()).mapToObj(Long::toString).collect(Collectors.joining(", "))
                + "\nmonthHistogram = " + LongStream.of(getMonthHistogram()).mapToObj(Long::toString).collect(Collectors.joining(", "));
    }
}
