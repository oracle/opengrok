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

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.web.api.ApiTask;
import org.opengrok.web.api.ApiTaskManager;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.opengrok.web.api.v1.controller.StatusController.PATH;

/**
 * API endpoint to check status of asynchronous requests.
 * Relies on {@link org.opengrok.web.api.v1.filter.IncomingFilter} to authorize the requests.
 */
@Path(PATH)
public class StatusController {

    public static final String PATH = "/status";

    private static final Logger LOGGER = LoggerFactory.getLogger(StatusController.class);

    @GET
    @Path("/{uuid}")
    public Response getStatus(@PathParam("uuid") String uuid) {
        ApiTask apiTask = ApiTaskManager.getInstance().getApiTask(uuid);
        if (apiTask == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (apiTask.isCompleted()) {
            return Response.status(apiTask.getResponseStatus()).build();
        } else {
            return Response.status(Response.Status.ACCEPTED).build();
        }
    }

    @DELETE
    @Path("/{uuid}")
    public Response delete(@PathParam("uuid") String uuid) {
        ApiTask apiTask = ApiTaskManager.getInstance().getApiTask(uuid);
        if (apiTask == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!apiTask.isCompleted()) {
            LOGGER.log(Level.WARNING, "API task {0} not yet completed", apiTask);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        ApiTaskManager.getInstance().deleteApiTask(uuid);

        return Response.ok().build();
    }
}
