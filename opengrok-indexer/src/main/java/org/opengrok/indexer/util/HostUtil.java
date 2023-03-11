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
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import org.opengrok.indexer.logger.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.opengrok.indexer.index.IndexerUtil.getWebAppHeaders;

/**
 * Utility class to provide simple host/address methods.
 */
public class HostUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(HostUtil.class);

    private HostUtil() {
        // private to enforce static
    }

    /**
     * @param urlStr URI
     * @return port number
     * @throws URISyntaxException on error
     */
    public static int urlToPort(String urlStr) throws URISyntaxException {
        URI uri = new URI(urlStr);
        return uri.getPort();
    }

    private static boolean isWebAppReachable(String webappURI, int timeOutMillis) {
        // TODO: token
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.connectTimeout(timeOutMillis, TimeUnit.MILLISECONDS);
        clientBuilder.readTimeout(timeOutMillis, TimeUnit.MILLISECONDS);

        try (Client client = clientBuilder.build()) {
            Response response = client
                .target(webappURI)
                    .path("api")
                    .path("v1")
                    .path("configuration")
                    .request()
                    .headers(getWebAppHeaders())
                    .get();

            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOGGER.log(Level.SEVERE, "cannot reach OpenGrok web application on {0}: {1}",
                        new Object[]{webappURI, response.getStatusInfo()});
                return false;
            }
        } catch (ProcessingException e) {
            LOGGER.log(Level.SEVERE, String.format("could not connect to %s", webappURI), e);
            return false;
        }

        return true;
    }

    public static boolean isReachable(String webappURI, int timeOutMillis) {
        boolean connectWorks = false;

        try {
            int port = HostUtil.urlToPort(webappURI);
            if (port <= 0) {
                LOGGER.log(Level.SEVERE, "invalid port number for " + webappURI);
                return false;
            }

            return isWebAppReachable(webappURI, timeOutMillis);
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, String.format("URI not valid: %s", webappURI), e);
        }

        return connectWorks;
    }
}
