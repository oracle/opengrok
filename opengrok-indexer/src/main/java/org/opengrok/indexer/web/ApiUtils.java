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
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jetbrains.annotations.NotNull;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiUtils.class);

    private ApiUtils() {
        // utility class
    }

    /**
     * Busy waits for API call to complete by repeatedly querying the status API endpoint passed
     * in the {@code Location} header in the response parameter. The overall time is governed
     * by the {@link RuntimeEnvironment#getApiTimeout()}, however each individual status check
     * uses {@link RuntimeEnvironment#getConnectTimeout()} so in the worst case the total time can be
     * {@code getApiTimeout() * getConnectTimeout()}.
     * @param response response returned from the server upon asynchronous API request
     * @return response from the status API call
     * @throws InterruptedException on sleep interruption
     * @throws IllegalArgumentException on invalid request (no {@code Location} header)
     */
    public static @NotNull Response waitForAsyncApi(@NotNull Response response)
            throws InterruptedException, IllegalArgumentException {

        String location = response.getHeaderString(HttpHeaders.LOCATION);
        if (location == null) {
            throw new IllegalArgumentException(String.format("no %s header in %s", HttpHeaders.LOCATION, response));
        }

        LOGGER.log(Level.FINER, "checking asynchronous API result on {0}", location);
        for (int i = 0; i < RuntimeEnvironment.getInstance().getApiTimeout(); i++) {
            response = ClientBuilder.newBuilder().
                    connectTimeout(RuntimeEnvironment.getInstance().getConnectTimeout(), TimeUnit.SECONDS).build().
                    target(location).request().get();
            if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
                Thread.sleep(1000);
            } else {
                break;
            }
        }

        if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
            LOGGER.log(Level.WARNING, "API request still not completed: {0}", response);
            return response;
        }

        LOGGER.log(Level.FINER, "making DELETE API request to {0}", location);
        Response deleteResponse = ClientBuilder.newBuilder().connectTimeout(3, TimeUnit.SECONDS).build().
                target(location).request().delete();
        if (deleteResponse.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            LOGGER.log(Level.WARNING, "DELETE API call to {0} failed with HTTP error {1}",
                    new Object[]{location, response.getStatusInfo()});
        }

        return response;
    }
}
