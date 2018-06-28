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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web.api.v1.filter;

import org.opensolaris.opengrok.logger.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
@PreMatching
public class LocalhostFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LocalhostFilter.class);

    @Context
    private HttpServletRequest request;

    private final Set<String> localAddresses = new HashSet<>();

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
        if (!localAddresses.contains(request.getRemoteAddr())) {
            context.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

}
