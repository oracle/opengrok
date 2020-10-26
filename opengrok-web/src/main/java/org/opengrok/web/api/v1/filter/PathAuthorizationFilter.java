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
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.filter;

import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves for authorization of REST API endpoints that have a path parameter
 * which is file path relative to source root.
 */
@Provider
@PathAuthorized
public class PathAuthorizationFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(PathAuthorizationFilter.class);

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    public static final String PATH_PARAM = "path";

    @Context
    private HttpServletRequest request;

    private static boolean isPathAuthorized(String path, HttpServletRequest request) {
        if (request != null) {
            AuthorizationFramework auth = env.getAuthorizationFramework();
            if (auth != null) {
                Project p = Project.getProject(path.startsWith("/") ? path : "/" + path);
                return p == null || auth.isAllowed(request, p);
            }
        }

        return false;
    }

    @Override
    public void filter(final ContainerRequestContext context) {
        if (request == null) { // happens in tests
            return;
        }

        String path = request.getParameter(PATH_PARAM);
        if (path == null || path.isEmpty()) {
            logger.log(Level.WARNING, "request does not contain \"{0}\" parameter: {1}",
                    new Object[]{PATH_PARAM, request});
            // This should align with whatever NoPathExceptionMapper does.
            // We cannot throw the exception here as it would not match the filter() signature.
            context.abortWith(Response.status(Response.Status.NOT_ACCEPTABLE).build());
            return;
        }

        if (!isPathAuthorized(path, request)) {
            // TODO: this should probably update statistics for denied requests like in AuthorizationFilter
            context.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            return; // for good measure
        }
    }
}
