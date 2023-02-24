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
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import jakarta.ws.rs.client.Client;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

public class IndexerUtil {

    private IndexerUtil() {
    }

    /**
     * @return map of HTTP headers to use when making API requests to the web application
     */
    public static MultivaluedMap<String, Object> getWebAppHeaders() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        String token = null;
        if ((token = RuntimeEnvironment.getInstance().getIndexerAuthenticationToken()) != null) {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return headers;
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

        try (Client client = ClientBuilder.newClient()) {
            final Invocation.Builder request = client.target(host)
                    .path("api")
                    .path("v1")
                    .path("configuration")
                    .path("projectsEnabled")
                    .request()
                    .headers(getWebAppHeaders());
            final String enabled = request.get(String.class);
            if (!Boolean.parseBoolean(enabled)) {
                try (final Response r = request.put(Entity.text(Boolean.TRUE.toString()))) {
                    if (r.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                        throw new WebApplicationException(String.format("Unable to enable projects: %s",
                                r.getStatusInfo().getReasonPhrase()), r.getStatus());
                    }
                }
            }
        }
    }
}
