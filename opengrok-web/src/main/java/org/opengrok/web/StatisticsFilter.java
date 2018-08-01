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
package org.opengrok.web;

import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.web.PageConfig;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.indexer.web.SearchHelper;
import org.opengrok.indexer.web.Statistics;

public class StatisticsFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsFilter.class);
    private static final String TIME_ATTRIBUTE = "statistics_time_start";

    @Override
    public void init(FilterConfig fc) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest sr, ServletResponse sr1, FilterChain fc)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) sr;
        HttpServletResponse httpRes = (HttpServletResponse) sr1;

        PageConfig config = PageConfig.get(httpReq);
        config.setRequestAttribute(TIME_ATTRIBUTE, System.currentTimeMillis());

        fc.doFilter(sr, sr1);

        if (httpReq.getRequestURI().replace(httpReq.getContextPath(), "").equals("/")
                || httpReq.getRequestURI().replace(httpReq.getContextPath(), "").equals("")) {
            collectStats(httpReq, "root");
        } else if (config.getPrefix() != Prefix.UNKNOWN) {
            String prefix = config.getPrefix().toString().substring(1);
            collectStats(httpReq, prefix);
        }
    }

    protected void collectStats(HttpServletRequest req, String category) {
        Object o;
        long processTime;
        PageConfig config = PageConfig.get(req);
        Statistics stats = config.getEnv().getStatistics();

        /**
         * Add the request to the statistics. Be aware of the colliding call in
         * {@code AuthorizationFilter#doFilter}.
         */
        stats.addRequest();

        if ((o = config.getRequestAttribute(TIME_ATTRIBUTE)) != null) {
            processTime = System.currentTimeMillis() - (long) o;

            stats.addRequestTime("*", processTime); // add to all
            stats.addRequestTime(category, processTime); // add this category

            /* supplementary categories */
            if (config.getProject() != null) {
                stats.addRequestTime("viewing_of_" + config.getProject().getName(), processTime);
            }

            SearchHelper helper = (SearchHelper) config.getRequestAttribute(SearchHelper.REQUEST_ATTR);
            if (helper != null) {
                if (helper.hits == null || helper.hits.length == 0) {
                    // empty search
                    stats.addRequestTime("empty_search", processTime);
                } else {
                    // successful search
                    stats.addRequestTime("successful_search", processTime);
                }
            }
        }
    }

    @Override
    public void destroy() {
    }
}
