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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.suggester.provider.filter;

import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Serves for authorization of specific REST API endpoints.
 */
@Provider
@Authorized
public class AuthorizationFilter implements ContainerRequestFilter {

    public static final String PROJECTS_PARAM = "projects[]";

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Context
    private HttpServletRequest request;

    /**
     * Checks if the request contains {@link #PROJECTS_PARAM} and if so then checks if the user has access to the
     * specified projects.
     * @param context request context
     */
    @Override
    public void filter(final ContainerRequestContext context) {
        if (request == null) { // happens in tests
            return;
        }
        AuthorizationFramework auth = env.getAuthorizationFramework();
        if (auth != null) {
            String[] projects = request.getParameterValues(PROJECTS_PARAM);
            if (projects != null) {
                for (String project : projects) {
                    Project p = Project.getByName(project);
                    if (!auth.isAllowed(request, p)) {
                        context.abortWith(Response.status(Response.Status.FORBIDDEN).build());
                    }
                }
            }
        }
    }

}
