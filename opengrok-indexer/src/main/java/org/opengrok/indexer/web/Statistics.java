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
package org.opengrok.indexer.web;

import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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

    private Map<String, Long> requestCategories = new TreeMap<>();
    private Map<String, Long> timing = new TreeMap<>();
    private Map<String, Long> timingMin = new TreeMap<>();
    private Map<String, Long> timingMax = new TreeMap<>();
    private long[] dayHistogram = new long[24];
    private long[] monthHistogram = new long[31];
    private long timeStart = System.currentTimeMillis();
    private long requests = 0;
    private long minutes = 1;
    private long requestsPerMinute = 0;
    private long requestsPerMinuteMin = Long.MAX_VALUE;
    private long requestsPerMinuteMax = Long.MIN_VALUE;

    /**
     * Adds a single request into all requests.
     */
    synchronized public void addRequest() {
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
    synchronized protected void maybeRefresh() {
        if (timeStart + 60 * 1000 <= System.currentTimeMillis()) {
            // several minutes have passed
            minutes += (System.currentTimeMillis() - timeStart) / (60 * 1000);
            timeStart = System.currentTimeMillis();
            requestsPerMinute = 0;
        }
    }

    /**
     * Adds a request into the category
     *
     * @param category category
     */
    synchronized public void addRequest(String category) {
        Long val = requestCategories.get(category);
        if (val == null) {
            val = 0L;
        }
        val += 1;
        requestCategories.put(category, val);
    }
    
    /**
     * Get value of given counter
     * @param category category
     * @return Long value
     */
    synchronized public Long getRequest(String category) {
        return requestCategories.get(category);
    }

    /**
     * Adds a request's process time into given category.
     *
     * @param category category
     * @param v time spent on processing this request
     */
    synchronized public void addRequestTime(String category, long v) {
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

    synchronized public void setRequestCategories(Map<String, Long> requestCategories) {
        this.requestCategories = requestCategories;
    }

    synchronized public void setTiming(Map<String, Long> timing) {
        this.timing = timing;
    }

    synchronized public void setTimingMin(Map<String, Long> timing_min) {
        this.timingMin = timing_min;
    }

    synchronized public void setTimingMax(Map<String, Long> timing_max) {
        this.timingMax = timing_max;
    }

    public long getTimeStart() {
        return timeStart;
    }

    synchronized public void setTimeStart(long timeStart) {
        this.timeStart = timeStart;
    }

    public long getRequests() {
        return requests;
    }

    synchronized public void setRequests(long requests) {
        this.requests = requests;
    }

    public long getMinutes() {
        maybeRefresh();
        return minutes;
    }

    synchronized public void setMinutes(long minutes) {
        this.minutes = minutes;
    }

    public long getRequestsPerMinute() {
        maybeRefresh();
        return requestsPerMinute;
    }

    synchronized public void setRequestsPerMinute(long requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public long getRequestsPerMinuteMin() {
        if (getRequests() <= 0) {
            return 0;
        }
        return requestsPerMinuteMin;
    }

    synchronized public void setRequestsPerMinuteMin(long requestsPerMinuteMin) {
        this.requestsPerMinuteMin = requestsPerMinuteMin;
    }

    public long getRequestsPerMinuteMax() {
        if (getRequests() <= 0) {
            return 0;
        }
        return requestsPerMinuteMax;
    }

    synchronized public void setRequestsPerMinuteMax(long requestsPerMinuteMax) {
        this.requestsPerMinuteMax = requestsPerMinuteMax;
    }

    public double getRequestsPerMinuteAvg() {
        maybeRefresh();
        return requests / (double) minutes;
    }

    public long[] getDayHistogram() {
        return dayHistogram;
    }

    synchronized public void setDayHistogram(long[] dayHistogram) {
        this.dayHistogram = dayHistogram;
    }

    public long[] getMonthHistogram() {
        return monthHistogram;
    }

    synchronized public void setMonthHistogram(long[] monthHistogram) {
        this.monthHistogram = monthHistogram;
    }

    /**
     * Convert this statistics object into JSONObject.
     *
     * @return the json object
     */
    public JSONObject toJson() {
        return toJson(this);
    }

    /**
     * Convert JSONObject object into statistics.
     *
     * @param input object containing statistics
     * @return the statistics object
     */
    @SuppressWarnings("unchecked")
    public static Statistics from(JSONObject input) {
        Statistics stats = new Statistics();
        Object o;
        if ((o = input.get(STATISTIC_REQUEST_CATEGORIES)) != null) {
            stats.setRequestCategories((Map<String, Long>) o);
        }
        if ((o = input.get(STATISTIC_TIMING)) != null) {
            stats.setTiming((Map<String, Long>) o);
        }
        if ((o = input.get(STATISTIC_TIMING_MIN)) != null) {
            stats.setTimingMin((Map<String, Long>) o);
        }
        if ((o = input.get(STATISTIC_TIMING_MAX)) != null) {
            stats.setTimingMax((Map<String, Long>) o);
        }
        if ((o = input.get(STATISTIC_REQUESTS)) != null) {
            stats.setRequests((long) o);
        }
        if ((o = input.get(STATISTIC_MINUTES)) != null) {
            stats.setMinutes((long) o);
        }
        if ((o = input.get(STATISTIC_REQUESTS_PER_MINUTE)) != null) {
            stats.setRequestsPerMinute((long) o);
        }
        if ((o = input.get(STATISTIC_REQUESTS_PER_MINUTE_MIN)) != null) {
            stats.setRequestsPerMinuteMin((long) o);
        }
        if ((o = input.get(STATISTIC_REQUESTS_PER_MINUTE_MAX)) != null) {
            stats.setRequestsPerMinuteMax((long) o);
        }
        if ((o = input.get(STATISTIC_DAY_HISTOGRAM)) != null) {
            stats.setDayHistogram(convertJSONArrayToArray((JSONArray) o, stats.getDayHistogram()));
        }
        if ((o = input.get(STATISTIC_MONTH_HISTOGRAM)) != null) {
            stats.setMonthHistogram(convertJSONArrayToArray((JSONArray) o, stats.getMonthHistogram()));
        }
        stats.setTimeStart(System.currentTimeMillis());
        return stats;
    }

    /**
     * Convert statistics object into JSONObject.
     *
     * @param stats the statistics object
     * @return the json object or empty json object if there was no request
     */
    @SuppressWarnings("unchecked")
    public static JSONObject toJson(Statistics stats) {
        JSONObject output = new JSONObject();
        if (stats.getRequests() == 0) {
            return output;
        }
        output.put(STATISTIC_REQUEST_CATEGORIES, new JSONObject(stats.getRequestCategories()));
        output.put(STATISTIC_TIMING, new JSONObject(stats.getTiming()));
        output.put(STATISTIC_TIMING_MIN, new JSONObject(stats.getTimingMin()));
        output.put(STATISTIC_TIMING_MAX, new JSONObject(stats.getTimingMax()));
        output.put(STATISTIC_TIMING_AVG, new JSONObject(stats.getTimingAvg()));
        output.put(STATISTIC_MINUTES, stats.getMinutes());
        output.put(STATISTIC_REQUESTS, stats.getRequests());
        output.put(STATISTIC_REQUESTS_PER_MINUTE, stats.getRequestsPerMinute());
        output.put(STATISTIC_REQUESTS_PER_MINUTE_MIN, stats.getRequestsPerMinuteMin());
        output.put(STATISTIC_REQUESTS_PER_MINUTE_MAX, stats.getRequestsPerMinuteMax());
        output.put(STATISTIC_REQUESTS_PER_MINUTE_AVG, stats.getRequestsPerMinuteAvg());
        output.put(STATISTIC_DAY_HISTOGRAM, convertArrayToJSONArray(stats.getDayHistogram()));
        output.put(STATISTIC_MONTH_HISTOGRAM, convertArrayToJSONArray(stats.getMonthHistogram()));
        return output;
    }

    /**
     * Converts an array into json array.
     *
     * @param array the input array
     * @return the output json array
     */
    @SuppressWarnings("unchecked")
    private static JSONArray convertArrayToJSONArray(long[] array) {
        JSONArray ret = new JSONArray();
        for (long o : array) {
            ret.add(o);
        }
        return ret;
    }

    /**
     * Converts an json array into an array.
     *
     * @param dest the input json array
     * @param target the output array
     * @return target
     */
    private static long[] convertJSONArrayToArray(JSONArray dest, long[] target) {
        for (int i = 0; i < target.length && i < dest.size(); i++) {
            target[i] = (long) dest.get(i);
        }
        return target;
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
                + "\ndayHistogram = " + LongStream.of(getDayHistogram()).mapToObj(a -> Long.toString(a)).map(a -> a.toString()).collect(Collectors.joining(", "))
                + "\nmonthHistogram = " + LongStream.of(getMonthHistogram()).mapToObj(a -> Long.toString(a)).map(a -> a.toString()).collect(Collectors.joining(", "));

    }
}
