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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

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
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.logger.LoggerFactory;

public class AuthorizationFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationFilter.class);

    @Override
    public void init(FilterConfig fc) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest sr, ServletResponse sr1, FilterChain fc) throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) sr;
        HttpServletResponse httpRes = (HttpServletResponse) sr1;

        PageConfig config = PageConfig.get(httpReq);
        long processTime = System.currentTimeMillis();

        Project p = config.getProject();
        if (p != null && !config.isAllowed(p)) {
            LOGGER.log(Level.INFO, "access denied for uri: {0}", httpReq.getRequestURI());
            config.getEnv().getStatistics().addRequest(httpReq);
            config.getEnv().getStatistics().addRequest(httpReq, "requests_forbidden");
            config.getEnv().getStatistics().addRequestTime(httpReq,
                    "requests_forbidden",
                    System.currentTimeMillis() - processTime);
            httpRes.sendError(403, "Access forbidden");
            return;
        }
        fc.doFilter(sr, sr1);
    }

    @Override
    public void destroy() {
    }

}
