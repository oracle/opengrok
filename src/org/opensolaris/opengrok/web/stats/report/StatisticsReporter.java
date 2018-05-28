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

import org.opensolaris.opengrok.web.stats.Statistics;

public abstract class StatisticsReporter {

    protected static final String TIMING = "timing";
    protected static final String TIMING_MIN = "timing_min";
    protected static final String TIMING_MAX = "timing_max";
    protected static final String TIMING_AVG = "timing_avg";
    protected static final String REQUEST_CATEGORIES = "request_categories";
    protected static final String REQUESTS = "requests";
    protected static final String MINUTES = "minutes";
    protected static final String REQUESTS_PER_MINUTE = "requests_per_minute";
    protected static final String REQUESTS_PER_MINUTE_MIN = "requests_per_minute_min";
    protected static final String REQUESTS_PER_MINUTE_MAX = "requests_per_minute_max";
    protected static final String REQUESTS_PER_MINUTE_AVG = "requests_per_minute_avg";
    protected static final String DAY_HISTOGRAM = "day_histogram";
    protected static final String MONTH_HISTOGRAM = "month_histogram";
    protected static final String SEARCH_REQUESTS = "search_requests";
    protected static final String ZERO_HIT_SEARCH_COUNT = "zero_hit_search_count";
    protected static final String AVERAGE_SEARCH_HITS = "average_search_hits";

    public abstract String report(Statistics statistics);

}
