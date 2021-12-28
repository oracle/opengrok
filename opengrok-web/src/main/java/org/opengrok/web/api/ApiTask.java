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
package org.opengrok.web.api;

import jakarta.ws.rs.core.Response;
import org.opengrok.indexer.logger.LoggerFactory;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents task associated with asynchronous API request.
 */
public class ApiTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiTask.class);

    private final Runnable runnable;

    enum ApiTaskState {
        INITIAL,
        SUBMITTED,
        COMPLETED,
    }
    private ApiTaskState state;

    private final UUID uuid;
    private final String path;

    private final Response.Status responseStatus;

    /**
     * @param path request path (for identification)
     * @param runnable Runnable object
     */
    public ApiTask(String path, Runnable runnable) {
        this(path, runnable, Response.Status.OK);
    }

    /**
     * @param path request path (for identification)
     * @param runnable Runnable object
     * @param status request status to return after the runnable is done
     */
    public ApiTask(String path, Runnable runnable, Response.Status status) {
        this.runnable = runnable;
        this.uuid = UUID.randomUUID();
        this.responseStatus = status;
        this.path = path;
        this.state = ApiTaskState.INITIAL;
    }

    public UUID getUuid() {
        return uuid;
    }

    /**
     * @return response status
     */
    public Response.Status getResponseStatus() {
        return responseStatus;
    }

    /**
     * Set status as submitted.
     */
    public void setSubmitted() {
        state = ApiTaskState.SUBMITTED;
    }

    public boolean isCompleted() {
        return state.equals(ApiTaskState.COMPLETED);
    }

    public void setCompleted() {
        state = ApiTaskState.COMPLETED;
    }

    /**
     * @return Runnable object that contains the work that needs to be completed for this API request
     */
    public Runnable getRunnable() {
        synchronized (this) {
            if (state.equals(ApiTaskState.SUBMITTED)) {
                throw new IllegalStateException(String.format("API task %s already submitted", this));
            }

            return () -> {
                LOGGER.log(Level.FINE, "API task {0} started", this);
                setSubmitted();
                runnable.run();
                setCompleted();
                LOGGER.log(Level.FINE, "API task {0} done", this);
            };
        }
    }

    @Override
    public String toString() {
        return "{uuid=" + uuid + ",path=" + path + ",state=" + state + ",responseStatus=" + responseStatus + "}";
    }
}
