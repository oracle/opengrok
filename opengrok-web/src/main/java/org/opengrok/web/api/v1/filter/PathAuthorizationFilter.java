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
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jetbrains.annotations.NotNull;
import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.web.util.FileUtil;
import org.opengrok.web.util.NoPathParameterException;

import java.io.IOException;
import java.nio.file.InvalidPathException;

/**
 * Serves for authorization of REST API endpoints that have a path parameter
 * which is file path relative to source root.
 */
@Provider
@PathAuthorized
public class PathAuthorizationFilter implements ContainerRequestFilter {

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    public static final String PATH_PARAM = "path";

    @Context
    private HttpServletRequest request;

    private static boolean isPathAuthorized(@NotNull HttpServletRequest request)
            throws IOException, NoPathParameterException {

        AuthorizationFramework auth = env.getAuthorizationFramework();
        if (auth != null) {
            /*
             * The 'path' parameter comes from the request, hence needs to be used in its canonical form
             * via getProject(file) before performing the isAllowed() check.
             */
            Project p = Project.getProject(FileUtil.toFile(request.getParameter(PATH_PARAM)));
            return p == null || auth.isAllowed(request, p);
        }

        return false;
    }

    @Override
    public void filter(final ContainerRequestContext context) throws IOException {
        if (request == null) { // happens in tests
            return;
        }

        try {
            if (!isPathAuthorized(request)) {
                context.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            }
        } catch (NoPathParameterException | InvalidPathException e) {
            context.abortWith(Response.status(Response.Status.NOT_ACCEPTABLE).build());
        }
    }
}
