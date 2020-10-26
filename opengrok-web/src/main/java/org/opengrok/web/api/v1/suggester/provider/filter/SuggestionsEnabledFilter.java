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

import org.opengrok.indexer.configuration.RuntimeEnvironment;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Filter which checks if suggester is enabled and if not then returns {@link Response.Status#NOT_FOUND}.
 */
@Provider
@Suggester
public class SuggestionsEnabledFilter implements ContainerRequestFilter {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Override
    public void filter(final ContainerRequestContext context) {
        if (!env.getSuggesterConfig().isEnabled()) {
            context.abortWith(Response.status(Response.Status.NOT_FOUND).build());
        }
    }
}
