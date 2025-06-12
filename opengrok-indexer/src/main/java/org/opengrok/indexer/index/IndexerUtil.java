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
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import org.opengrok.indexer.configuration.Project;
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
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.web.Util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.opengrok.indexer.web.ApiUtils.waitForAsyncApi;

public class IndexerUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexerUtil.class);

    private IndexerUtil() {
    }

    /**
     * @return map of HTTP headers to use when making API requests to the web application
     */
    public static MultivaluedMap<String, Object> getWebAppHeaders() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        String token;
        if ((token = RuntimeEnvironment.getInstance().getIndexerAuthenticationToken()) != null) {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return headers;
    }

    /**
     * Enable projects in the remote application.
     * <p>
     * NOTE: performs a check if the projects are already enabled,
     * before making the change request
     *
     * @param webappUri the url to the remote web application
     * @throws ResponseProcessingException in case processing of a received HTTP response fails
     * @throws ProcessingException         in case the request processing or subsequent I/O operation fails
     * @throws WebApplicationException     in case the response status code of the response returned by the server is not successful
     */
    public static void enableProjects(final String webappUri) throws
            ResponseProcessingException,
            ProcessingException,
            WebApplicationException {

        try (Client client = ClientBuilder.newBuilder().
                connectTimeout(RuntimeEnvironment.getInstance().getConnectTimeout(), TimeUnit.SECONDS).build()) {
            final Invocation.Builder request = client.target(webappUri)
                    .path("api")
                    .path("v1")
                    .path("configuration")
                    .path("projectsEnabled")
                    .request()
                    .headers(getWebAppHeaders());
            final String enabled = request.get(String.class);
            if (!Boolean.parseBoolean(enabled)) {
                try (Response r = request.put(Entity.text(Boolean.TRUE.toString()))) {
                    if (r.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                        throw new WebApplicationException(String.format("Unable to enable projects: %s",
                                r.getStatusInfo().getReasonPhrase()), r.getStatus());
                    }
                }
            }
        }
    }

    /**
     * Mark project as indexed via API call. Assumes the project is already known to the webapp.
     * @param webappUri URI for the webapp
     * @param project project to mark as indexed
     */
    public static void markProjectIndexed(String webappUri, Project project) {
        Response response;
        try (Client client = ClientBuilder.newBuilder().
                connectTimeout(RuntimeEnvironment.getInstance().getConnectTimeout(), TimeUnit.SECONDS).build()) {
            response = client.target(webappUri)
                    .path("api")
                    .path("v1")
                    .path("projects")
                    .path(Util.uriEncode(project.getName()))
                    .path("indexed")
                    .request()
                    .headers(getWebAppHeaders())
                    .put(Entity.text(""));

            if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
                try {
                    response = waitForAsyncApi(response);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "interrupted while waiting for API response", e);
                }
            }

            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOGGER.log(Level.WARNING, "Could not notify the webapp that project {0} was indexed: {1}",
                        new Object[] {project, response});
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, String.format("Could not notify the webapp that project %s was indexed",
                    project), e);
        }
    }

    /**
     * @param webappUri URI for the webapp
     * @return list of projects known to the webapp
     */
    public static Collection<String> getProjects(String webappUri) {
        try (Client client = ClientBuilder.newBuilder().
                connectTimeout(RuntimeEnvironment.getInstance().getConnectTimeout(), TimeUnit.SECONDS).build()) {
            final Invocation.Builder request = client.target(webappUri)
                    .path("api")
                    .path("v1")
                    .path("projects")
                    .request(MediaType.APPLICATION_JSON)
                    .headers(getWebAppHeaders());
            return request.get(new GenericType<List<String>>(){});
        }
    }
}
