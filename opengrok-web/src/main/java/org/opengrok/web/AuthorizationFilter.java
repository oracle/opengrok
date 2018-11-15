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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.web.PageConfig;
import org.opengrok.web.api.v1.RestApp;

public class AuthorizationFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationFilter.class);

    @Override
    public void init(FilterConfig fc) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest sr, ServletResponse sr1, FilterChain fc) throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) sr;
        HttpServletResponse httpRes = (HttpServletResponse) sr1;

        // All RESTful API requests are allowed for now (also see LocalhostFilter).
        // The /search endpoint will go through authorization via SearchEngine.search()
        // so does not have to be exempted here.
        if (httpReq.getServletPath().startsWith(RestApp.API_PATH)) {
            LOGGER.log(Level.FINER, "Allowing request to {0} in {1}",
                    new Object[]{httpReq.getServletPath(), AuthorizationFilter.class.getName() });
            fc.doFilter(sr, sr1);
            return;
        }

        PageConfig config = PageConfig.get(httpReq);
        long processTime = System.currentTimeMillis();

        Project p = config.getProject();
        if (p != null && !config.isAllowed(p)) {
            if (httpReq.getRemoteUser() != null) {
                LOGGER.log(Level.INFO, "Access denied for user ''{0}'' for URI: {1}",
                        new Object[]{httpReq.getRemoteUser(),
                            httpReq.getRequestURI()});
            } else {
                LOGGER.log(Level.INFO, "Access denied for URI: {0}", httpReq.getRequestURI());
            }

            /*
             * Add the request to the statistics. This is called just once for a
             * single request otherwise the next filter will count the same
             * request twice ({@link StatisticsFilter#collectStats}).
             *
             * In this branch of the if statement the filter processing stopped
             * and does not follow to the StatisticsFilter.
             */
            config.getEnv().getStatistics().addRequest();
            config.getEnv().getStatistics().addRequest("requests_forbidden");
            config.getEnv().getStatistics().addRequestTime("requests_forbidden",
                    System.currentTimeMillis() - processTime);

            if (!config.getEnv().getIncludeFiles().getForbiddenIncludeFileContent(false).isEmpty()) {
                sr.getRequestDispatcher("/eforbidden").forward(sr, sr1);
                return;
            }

            httpRes.sendError(HttpServletResponse.SC_FORBIDDEN, "Access forbidden");
            return;
        }
        fc.doFilter(sr, sr1);
    }

    @Override
    public void destroy() {
    }

}
