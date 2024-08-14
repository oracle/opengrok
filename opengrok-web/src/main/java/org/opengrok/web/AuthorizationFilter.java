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
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.opengrok.indexer.configuration.IncludeFiles;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.web.Laundromat;
import org.opengrok.web.api.v1.RestApp;

public class AuthorizationFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationFilter.class);

    @Override
    public void init(FilterConfig fc) {
        // Empty since there is no specific init configuration.
    }

    /**
     * All RESTful API requests are allowed here because they go through
     * {@link org.opengrok.web.api.v1.filter.IncomingFilter}.
     * The /search endpoint will go through authorization via SearchEngine.search()
     * so does not have to be exempted here.
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain fc)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        if (httpRequest.getServletPath().startsWith(RestApp.API_PATH)) {
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.log(Level.FINER, "Allowing request to {0} in {1}",
                        new Object[] {Laundromat.launderLog(httpRequest.getServletPath()),
                                AuthorizationFilter.class.getName()});
            }
            fc.doFilter(servletRequest, servletResponse);
            return;
        }

        PageConfig config = PageConfig.get(httpRequest);
        Project p = config.getProject();
        if (p != null && !config.isAllowed(p)) {
            if (LOGGER.isLoggable(Level.INFO)) {
                if (httpRequest.getRemoteUser() != null) {
                    LOGGER.log(Level.INFO, "Access denied for user ''{0}'' for URI: {1}",
                            new Object[] {Laundromat.launderLog(httpRequest.getRemoteUser()),
                                    Laundromat.launderLog(httpRequest.getRequestURI())});
                } else {
                    LOGGER.log(Level.INFO, "Access denied for URI: {0}",
                            Laundromat.launderLog(httpRequest.getRequestURI()));
                }
            }

            IncludeFiles includeFiles = config.getEnv().getIncludeFiles();
            if (Objects.nonNull(includeFiles) && !includeFiles.getForbiddenIncludeFileContent(false).isEmpty()) {
                servletRequest.getRequestDispatcher("/eforbidden").forward(servletRequest, servletResponse);
                return;
            }

            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Access forbidden");
            return;
        }
        fc.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
        // Empty since there is No specific destroy configuration.
    }
}
