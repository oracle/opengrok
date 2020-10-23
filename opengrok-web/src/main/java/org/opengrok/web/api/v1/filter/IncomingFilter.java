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
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.filter;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.web.api.v1.controller.AnnotationController;
import org.opengrok.web.api.v1.controller.FileController;
import org.opengrok.web.api.v1.controller.HistoryController;
import org.opengrok.web.api.v1.controller.SearchController;
import org.opengrok.web.api.v1.controller.SuggesterController;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This filter allows the request in case it contains the correct authentication token
 * (needs to come in via HTTPS) or it is coming from localhost or its path matches the list
 * of built in paths.
 */
@Provider
@PreMatching
public class IncomingFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(IncomingFilter.class);

    /**
     * Endpoint paths that are exempted from this filter.
     * @see SearchController#search(HttpServletRequest, String, String, String, String, String, String,
     * java.util.List, int, int)
     * @see SuggesterController#getSuggestions(org.opengrok.web.api.v1.suggester.model.SuggesterQueryData)
     * @see SuggesterController#getConfig()
     */
    private static final Set<String> allowedPaths = new HashSet<>(Arrays.asList(
            SearchController.PATH, SuggesterController.PATH, SuggesterController.PATH + "/config",
            HistoryController.PATH, FileController.PATH, AnnotationController.PATH));

    @Context
    private HttpServletRequest request;

    private final Set<String> localAddresses = new HashSet<>(Arrays.asList(
            "127.0.0.1", "0:0:0:0:0:0:0:1", "localhost"
    ));

    @PostConstruct
    public void init() {
        try {
            localAddresses.add(InetAddress.getLocalHost().getHostAddress());
            for (InetAddress inetAddress : InetAddress.getAllByName("localhost")) {
                localAddresses.add(inetAddress.getHostAddress());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not get localhost addresses", e);
        }
    }

    @Override
    public void filter(final ContainerRequestContext context) {
        if (request == null) { // happens in tests
            return;
        }

        String path = context.getUriInfo().getPath();

        if (request.isSecure()) {
            String authHeader;
            if (((authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)) != null) &&
                    RuntimeEnvironment.getInstance().getAuthenticationTokens().contains(authHeader)) {
                logger.log(Level.FINEST, "allowing request to {0} based on authentication header", path);
                return;
            }
        }

        if (allowedPaths.contains(path)) {
            logger.log(Level.FINEST, "allowing request to {0} based on whitelisted path", path);
            return;
        }

        if (localAddresses.contains(request.getRemoteAddr())) {
            logger.log(Level.FINEST, "allowing request to {0} based on localhost IP address", path);
            return;
        }

        context.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
}
