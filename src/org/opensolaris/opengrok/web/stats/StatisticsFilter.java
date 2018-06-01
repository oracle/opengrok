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

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.opensolaris.opengrok.web.PageConfig;
import org.opensolaris.opengrok.web.Prefix;
import org.opensolaris.opengrok.web.SearchHelper;

public class StatisticsFilter implements Filter {

    private static final String TIME_ATTRIBUTE = "statistics_time_start";

    @Override
    public void init(FilterConfig fc) {
    }

    @Override
    public void doFilter(ServletRequest sr, ServletResponse sr1, FilterChain fc)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) sr;

        PageConfig config = PageConfig.get(httpReq);
        config.setRequestAttribute(TIME_ATTRIBUTE, Instant.now());

        fc.doFilter(sr, sr1);

        String requestString = getRequestString(httpReq);
        if (requestString.equals("/") || requestString.isEmpty()) {
            collectStats(config, "root");
        } else if (config.getPrefix() != Prefix.UNKNOWN) {
            String prefix = config.getPrefix().toString().substring(1);
            collectStats(config, prefix);
        }
    }

    private String getRequestString(final HttpServletRequest httpReq) {
        return httpReq.getRequestURI().replace(httpReq.getContextPath(), "");
    }

    /**
     * Add the request to the statistics. Be aware of the colliding call in
     * {@link org.opensolaris.opengrok.web.AuthorizationFilter#doFilter}.
     */
    private void collectStats(PageConfig config, String category) {
        Statistics stats = config.getEnv().getStatistics();

        Object timeAttr;
        if ((timeAttr = config.getRequestAttribute(TIME_ATTRIBUTE)) != null) {
            Duration processTime = Duration.between((Instant) timeAttr, Instant.now());

            List<String> categories = new ArrayList<>(3);
            categories.add("*");
            categories.add(category);

            /* supplementary categories */
            if (config.getProject() != null) {
                categories.add("viewing_of_" + config.getProject().getName());
            }

            SearchHelper helper = (SearchHelper) config.getRequestAttribute(SearchHelper.REQUEST_ATTR);

            stats.addRequest(categories, processTime, helper);
        } else {
            stats.addRequest(category);
        }
    }

    @Override
    public void destroy() {
    }
}
