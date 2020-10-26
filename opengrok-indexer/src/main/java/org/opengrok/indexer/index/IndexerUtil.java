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
package org.opengrok.indexer.index;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.core.Response;

class IndexerUtil {

    private IndexerUtil() {
    }

    /**
     * Enable projects in the remote host application.
     * <p>
     * NOTE: performs a check if the projects are already enabled,
     * before making the change request
     *
     * @param host the url to the remote host
     * @throws ResponseProcessingException in case processing of a received HTTP response fails
     * @throws ProcessingException         in case the request processing or subsequent I/O operation fails
     * @throws WebApplicationException     in case the response status code of the response returned by the server is not successful
     */
    public static void enableProjects(final String host) throws
            ResponseProcessingException,
            ProcessingException,
            WebApplicationException {
        final Invocation.Builder request = ClientBuilder.newClient()
                                                        .target(host)
                                                        .path("api")
                                                        .path("v1")
                                                        .path("configuration")
                                                        .path("projectsEnabled")
                                                        .request();
        final String enabled = request.get(String.class);
        if (enabled == null || !Boolean.valueOf(enabled)) {
            final Response r = request.put(Entity.text(Boolean.TRUE.toString()));
            if (r.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new WebApplicationException(String.format("Unable to enable projects: %s", r.getStatusInfo().getReasonPhrase()), r.getStatus());
            }
        }
    }
}
