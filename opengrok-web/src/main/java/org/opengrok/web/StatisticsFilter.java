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
package org.opengrok.web;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.web.PageConfig;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.indexer.web.SearchHelper;

public class StatisticsFilter implements Filter {

    static final String REQUESTS_METRIC = "requests";

    private final Meter requests = Metrics.getInstance().meter(REQUESTS_METRIC);

    private final Timer genericTimer = Metrics.getInstance().timer("*");
    private final Timer emptySearch = Metrics.getInstance().timer("empty_search");
    private final Timer successfulSearch = Metrics.getInstance().timer("successful_search");

    @Override
    public void init(FilterConfig fc) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest sr, ServletResponse sr1, FilterChain fc)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) sr;

        Instant start = Instant.now();

        PageConfig config = PageConfig.get(httpReq);

        fc.doFilter(sr, sr1);

        Duration duration = Duration.between(start, Instant.now());

        String category;
        if (isRoot(httpReq)) {
            category = "root";
        } else if (config.getPrefix() != Prefix.UNKNOWN) {
            category = config.getPrefix().toString().substring(1);
        } else {
            return;
        }

        /*
         * Add the request to the statistics. Be aware of the colliding call in
         * {@code AuthorizationFilter#doFilter}.
         */
        requests.mark();
        genericTimer.update(duration);

        Metrics.getInstance().timer(category).update(duration);

        /* supplementary categories */
        if (config.getProject() != null) {
            Metrics.getInstance()
                    .timer("viewing_of_" + config.getProject().getName())
                    .update(duration);
        }

        SearchHelper helper = (SearchHelper) config.getRequestAttribute(SearchHelper.REQUEST_ATTR);
        if (helper != null) {
            if (helper.hits == null || helper.hits.length == 0) {
                // empty search
                emptySearch.update(duration);
            } else {
                // successful search
                successfulSearch.update(duration);
            }
        }
    }

    private boolean isRoot(final HttpServletRequest httpReq) {
        return httpReq.getRequestURI().replace(httpReq.getContextPath(), "").equals("/")
                || httpReq.getRequestURI().replace(httpReq.getContextPath(), "").equals("");
    }

    @Override
    public void destroy() {
    }
}
