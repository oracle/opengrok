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
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.indexer.web.SearchHelper;

public class StatisticsFilter implements Filter {

    static final String REQUESTS_METRIC = "requests";
    private static final String CATEGORY_TAG = "category";

    private final DistributionSummary requests = Metrics.getPrometheusRegistry().summary(REQUESTS_METRIC);

    @Override
    public void init(FilterConfig fc) throws ServletException {
        //No init config Operation
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain fc)
            throws IOException, ServletException {

        requests.record(1);

        HttpServletRequest httpReq = (HttpServletRequest) servletRequest;

        Instant start = Instant.now();

        PageConfig config = PageConfig.get(httpReq);

        fc.doFilter(servletRequest, servletResponse);

        measure((HttpServletResponse) servletResponse, httpReq, Duration.between(start, Instant.now()), config);
    }

    private void measure(HttpServletResponse httpResponse, HttpServletRequest httpReq,
                         Duration duration, PageConfig config) {
        String category;
        category = getCategory(httpReq, config);

        Timer categoryTimer = Timer.builder("requests.latency").
                tags(CATEGORY_TAG, category, "code", String.valueOf(httpResponse.getStatus())).
                register(Metrics.getPrometheusRegistry());
        categoryTimer.record(duration);

        SearchHelper helper = (SearchHelper) config.getRequestAttribute(SearchHelper.REQUEST_ATTR);
        MeterRegistry registry = Metrics.getRegistry();
        if (helper != null && registry != null) {
            if (helper.getHits() == null || helper.getHits().length == 0) {
                Timer.builder("search.latency").
                        tags(CATEGORY_TAG, "ui", "outcome", "empty").
                        register(registry).
                        record(duration);
            } else {
                Timer.builder("search.latency").
                        tags(CATEGORY_TAG, "ui", "outcome", "success").
                        register(registry).
                        record(duration);
            }
        }
    }

    @NotNull
    private String getCategory(HttpServletRequest httpReq, PageConfig config) {
        String category;
        if (isRoot(httpReq)) {
            category = "root";
        } else {
            String prefix = config.getPrefix().toString();
            if (prefix.isEmpty()) {
                category = "unknown";
            } else {
                category = prefix.substring(1);
                if (category.equals("xref") && httpReq.getParameter(QueryParameters.ANNOTATION_PARAM) != null) {
                    category = "annotate";
                }
            }
        }
        return category;
    }

    private boolean isRoot(final HttpServletRequest httpReq) {
        return httpReq.getRequestURI().replace(httpReq.getContextPath(), "").equals("/")
                || httpReq.getRequestURI().replace(httpReq.getContextPath(), "").equals("");
    }

    @Override
    public void destroy() {
        //No destroy Operation
    }
}
