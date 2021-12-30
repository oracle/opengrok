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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.opengrok.web.api.ApiTask;
import org.opengrok.web.api.ApiTaskManager;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApiUtils {
    private ApiUtils() {
        // utility class
    }

    /**
     * Busy-waits the status of asynchronous API call, mimicking
     * {@link org.opengrok.indexer.configuration.RuntimeEnvironment#waitForAsyncApi(Response)},
     * however side-steps status API check by going to the {@link ApiTaskManager} directly in order to avoid
     * going through the {@link StatusController} as it might not be deployed in the unit tests.
     * The method will return right away if the status of the response object parameter is not
     * {@code Response.Status.ACCEPTED}.
     * @param response API Response object
     * @return the response object
     */
    protected static Response waitForTask(Response response) {
        if (response.getStatus() != Response.Status.ACCEPTED.getStatusCode()) {
            return response;
        }

        String location = response.getHeaderString(HttpHeaders.LOCATION);
        String locationUri = URI.create(location).getPath();
        final String apiPrefix = "api/v1/status/";
        assertTrue(locationUri.contains(apiPrefix));
        int idx = locationUri.indexOf(apiPrefix);
        assertTrue(idx > 0);
        String uuid = locationUri.substring(idx + apiPrefix.length());
        ApiTask apiTask = ApiTaskManager.getInstance().getApiTask(uuid);
        assertNotNull(apiTask);
        await().atMost(16, TimeUnit.SECONDS).until(apiTask::isCompleted);

        if (!apiTask.isDone()) {
            return response;
        } else {
            return apiTask.getResponse();
        }
    }
}
