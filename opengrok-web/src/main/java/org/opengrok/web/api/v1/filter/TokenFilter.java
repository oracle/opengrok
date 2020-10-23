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
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 */

package org.opengrok.web.api.v1.filter;

import org.opengrok.indexer.configuration.RuntimeEnvironment;

import javax.annotation.Priority;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;

@Provider
@PreMatching
@Priority(1) // needs to be before LocalhostFilter
public class TokenFilter implements ContainerRequestFilter {
    public static final String TOKEN_AUTHORIZED = "OpenGrokTokenFilterResult";

    @Context
    private HttpServletRequest request;

    @Override
    public void filter(final ContainerRequestContext context) {
        if (request == null) { // happens in tests
            return;
        }

        request.setAttribute(TOKEN_AUTHORIZED, false);

        /*
        TODO
        if (!request.isSecure()) {
            return;
        }
         */

        String path = context.getUriInfo().getPath();
        if (LocalhostFilter.allowedPaths.contains(path)) {
            return;
        }

        String authHeader;
        if ((authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)) != null) {
            if (RuntimeEnvironment.getInstance().getTokens().contains(authHeader)) {
                request.setAttribute(TOKEN_AUTHORIZED, true);
            }
        }
    }
}
