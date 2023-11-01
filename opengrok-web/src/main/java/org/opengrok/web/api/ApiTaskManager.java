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
package org.opengrok.web.api;

import jakarta.ws.rs.core.Response;
import org.opengrok.indexer.configuration.OpenGrokThreadFactory;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.web.api.v1.controller.StatusController;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages asynchronous API requests.
 */
@SuppressWarnings("java:S6548")
public final class ApiTaskManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiTaskManager.class);

    private final Map<String, ExecutorService> queues = new ConcurrentHashMap<>(); // queue name -> ExecutorService
    private final Map<UUID, ApiTask> apiTasks = new ConcurrentHashMap<>(); // UUID -> ApiTask

    private static final ApiTaskManager apiTaskManager = new ApiTaskManager();

    private String contextPath;

    private ApiTaskManager() {
        // singleton
    }

    public static ApiTaskManager getInstance() {
        return apiTaskManager;
    }

    /**
     * @param uuid UUID
     * @return ApiTask object or null
     */
    public ApiTask getApiTask(String uuid) {
        return apiTasks.get(UUID.fromString(uuid));
    }

    /**
     * Set context path. This is used to construct status URLs in {@link #submitApiTask(String, ApiTask)}
     * @param path context path
     */
    public void setContextPath(String path) {
        this.contextPath = path;
    }

    static String getQueueName(String name) {
        String suffix = name.replaceAll("^/", "").replace("/", "-");
        if (!suffix.startsWith("-")) {
             suffix = "-" + suffix;
        }
        return "api_task" + suffix;
    }

    /**
     * Submit an API task for processing.
     * @param name name of the API endpoint
     * @param apiTask ApiTask object
     * @return Response status
     */
    public Response submitApiTask(String name, ApiTask apiTask) {
        String queueName = getQueueName(name);
        if (queues.get(queueName) == null) {
            LOGGER.log(Level.WARNING, "cannot find queue ''{0}''", queueName);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        apiTask.setFuture(queues.get(queueName).submit(apiTask.getCallable()));
        apiTasks.put(apiTask.getUuid(), apiTask);

        // This is useful for testing where there is no context path.
        var uriStringBuilder = Optional.ofNullable(contextPath)
                .map(StringBuilder::new)
                .map(stringBuilder -> stringBuilder.append("/api/v1"))
                .orElseGet(StringBuilder::new);
        uriStringBuilder.append("/");
        uriStringBuilder.append(StatusController.PATH);
        uriStringBuilder.append("/");
        uriStringBuilder.append(apiTask.getUuid());

        return Response.status(Response.Status.ACCEPTED).
                location(URI.create(uriStringBuilder.toString())).build();
    }

    /**
     * Delete API task.
     * @param uuid task UUID
     */
    public void deleteApiTask(String uuid) {
        apiTasks.remove(UUID.fromString(uuid));
    }

    /**
     * Add new thread pool.
     * @param name name of the thread pool
     * @param threadCount thread count
     */
    public void addPool(String name, int threadCount) {
        String queueName = getQueueName(name);

        if (queues.get(queueName) != null) {
            throw new IllegalStateException(String.format("queue %s already present", queueName));
        }

        queues.put(queueName, Executors.newFixedThreadPool(threadCount,
                new OpenGrokThreadFactory(queueName)));
    }

    /**
     * Shutdown all executor services and wait 60 seconds for pending tasks.
     * @throws InterruptedException on termination
     */
    public synchronized void shutdown() throws InterruptedException {
        for (ExecutorService executorService : queues.values()) {
            executorService.shutdown();
        }

        for (Map.Entry<String, ExecutorService> entry : queues.entrySet()) {
            boolean shutdownResult = entry.getValue().awaitTermination(60, TimeUnit.SECONDS);
            if (!shutdownResult) {
                LOGGER.log(Level.WARNING, "abnormal termination for executor service for queue ''{0}''",
                        entry.getKey());
            }
        }
    }
}
