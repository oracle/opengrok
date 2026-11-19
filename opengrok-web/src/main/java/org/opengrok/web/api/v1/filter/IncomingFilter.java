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
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.filter;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.opengrok.indexer.configuration.ConfigurationChangedListener;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.web.api.v1.controller.AnnotationController;
import org.opengrok.web.api.v1.controller.FileController;
import org.opengrok.web.api.v1.controller.HistoryController;
import org.opengrok.web.api.v1.controller.SearchController;
import org.opengrok.web.api.v1.controller.SuggesterController;
import org.opengrok.web.api.v1.controller.SystemController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This filter allows the request in case it contains the correct authentication bearer token
 * (needs to come in via HTTPS unless insecure tokens are explicitly allowed) or its path matches
 * the list of built-in paths.
 */
@Provider
@PreMatching
public class IncomingFilter implements ContainerRequestFilter, ConfigurationChangedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingFilter.class);

    /**
     * Endpoint paths that are exempted from this filter.
     * @see SearchController#search(HttpServletRequest, String, String, String, String, String, String,
     * java.util.List, int, int, String, int)
     * @see SuggesterController#getSuggestions(org.opengrok.web.api.v1.suggester.model.SuggesterQueryData)
     * @see SuggesterController#getConfig()
     */
    private static final Set<String> allowedPaths = new HashSet<>(Arrays.asList(
            SearchController.PATH, SuggesterController.PATH, SuggesterController.PATH + "/config",
            HistoryController.PATH, FileController.PATH + "/content", FileController.PATH + "/genre",
            FileController.PATH + "/defs", AnnotationController.PATH,
            SystemController.PATH + "/ping", SystemController.PATH + "/" + SystemController.INDEX_TIME ));

    @Context
    private HttpServletRequest request;

    static final String BEARER = "Bearer ";  // Authorization header value prefix

    private Set<String> tokens;

    private Set<String> getTokens() {
        return tokens;
    }

    private void setTokens(Set<String> tokens) {
        this.tokens = tokens;
    }

    @PostConstruct
    public void init() {
        // Cache the tokens to avoid locking.
        setTokens(RuntimeEnvironment.getInstance().getAuthenticationTokens());

        RuntimeEnvironment.getInstance().registerListener(this);
    }

    @Override
    public void onConfigurationChanged() {
        LOGGER.log(Level.FINER, "refreshing token cache");
        setTokens(RuntimeEnvironment.getInstance().getAuthenticationTokens());
    }

    @Override
    public void filter(final ContainerRequestContext context) {
        if (request == null) { // happens in tests
            return;
        }

        String path = context.getUriInfo().getPath();
        boolean isTokenValid = Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                .filter(authHeaderValue -> authHeaderValue.startsWith(BEARER))
                .map(authHeaderValue -> authHeaderValue.substring(BEARER.length()))
                .filter(getTokens()::contains)
                .isPresent();
        if (isTokenValid) {
            var sanitizedPath = path.replaceAll("[\n\r]", "_");
            if (request.isSecure() || RuntimeEnvironment.getInstance().isAllowInsecureTokens()) {
                LOGGER.log(Level.FINEST, "allowing request to {0} based on authentication token", sanitizedPath);
                return;
            } else {
                LOGGER.log(Level.WARNING, "request to {0} has a valid token however is not secure", sanitizedPath);
            }
        }

        if (allowedPaths.contains(path)) {
            LOGGER.log(Level.FINEST, "allowing request to {0} based on allow listed path", path);
            return;
        }

        context.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
}
