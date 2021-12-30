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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents task associated with asynchronous API request.
 */
public class ApiTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiTask.class);

    private final Callable<Object> callable;

    enum ApiTaskState {
        INITIAL,
        SUBMITTED,
        COMPLETED,
    }
    private ApiTaskState state;

    private final UUID uuid;
    private final String path;

    private final Response.Status responseStatus;

    private Future<Object> future;

    private final Map<Class<?>, Response.Status> exceptionStatusMap = new HashMap<>();

    /**
     * @param path request path (for identification)
     * @param callable Callable object
     */
    public ApiTask(String path, Callable<Object> callable) {
        this(path, callable, Response.Status.OK);
    }

    /**
     * @param path request path (for identification)
     * @param callable Callable object
     * @param status request status to return after the runnable is done
     */
    public ApiTask(String path, Callable<Object> callable, Response.Status status) {
        this(path, callable, status, null);
    }

   /**
    * @param path request path (for identification)
    * @param callable Callable object
    * @param status request status to return after the runnable is done
    * @param exceptionStatusMap map of {@link Exception} to {@link Response.Status}
    */
    public ApiTask(String path, Callable<Object> callable, Response.Status status,
                   Map<Class<?>, Response.Status> exceptionStatusMap) {
        this.callable = callable;
        this.uuid = UUID.randomUUID();
        this.responseStatus = status;
        this.path = path;
        this.state = ApiTaskState.INITIAL;
        if (exceptionStatusMap != null) {
            this.exceptionStatusMap.putAll(exceptionStatusMap);
        }
    }

    /**
     * The UUID is randomly generated in the constructor.
     * @return UUID
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * @return response status
     */
    Response.Status getResponseStatus() {
        return responseStatus;
    }

    /**
     * Set status as submitted.
     */
    void setSubmitted() {
        state = ApiTaskState.SUBMITTED;
    }

    /**
     * @return whether the API task successfully completed
     */
    public boolean isCompleted() {
        return state.equals(ApiTaskState.COMPLETED);
    }

    void setCompleted() {
        state = ApiTaskState.COMPLETED;
    }

    /**
     * @param future Future object used for tracking the progress of the API task
     */
    public void setFuture(Future<Object> future) {
        this.future = future;
    }

    /**
     * @return whether the task is finished (normally or with exception)
     */
    public boolean isDone() {
        if (future != null) {
            return future.isDone();
        } else {
            return false;
        }
    }

    private Response.Status mapExceptionToStatus(ExecutionException exception) {
        return exceptionStatusMap.getOrDefault(exception.getCause().getClass(), Response.Status.INTERNAL_SERVER_ERROR);
    }

    /**
     * This method assumes that the API task is finished.
     * @return response object corresponding to the state of the API task
     * @throws IllegalStateException if the API task is not finished
     */
    public Response getResponse() {
        // Avoid thread being blocked in future.get() below.
        if (!isDone()) {
            throw new IllegalStateException(String.format("task %s not yet done", this));
        }

        Object obj;
        try {
            obj = future.get();
        } catch (InterruptedException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } catch (ExecutionException ex) {
            return Response.status(mapExceptionToStatus(ex)).entity(ex.toString()).build();
        }

        if (obj != null) {
            return Response.status(getResponseStatus()).entity(obj.toString()).build();
        }

        return Response.status(getResponseStatus()).build();
    }

    /**
     * @return Runnable object that contains the work that needs to be completed for this API request
     */
    public Callable<Object> getCallable() {
        synchronized (this) {
            if (state.equals(ApiTaskState.SUBMITTED)) {
                throw new IllegalStateException(String.format("API task %s already submitted", this));
            }

            return new Callable<>() {
                public Object call() throws Exception {
                    LOGGER.log(Level.FINE, "API task {0} started", this);
                    setSubmitted();
                    Object ret = callable.call();
                    setCompleted();
                    LOGGER.log(Level.FINE, "API task {0} done", this);
                    return ret;
                }
            };
        }
    }

    @Override
    public String toString() {
        return "{uuid=" + uuid + ",path=" + path + ",state=" + state + ",responseStatus=" + responseStatus + "}";
    }
}
