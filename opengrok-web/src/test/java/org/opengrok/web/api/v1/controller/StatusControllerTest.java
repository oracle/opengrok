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

import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.opengrok.web.api.ApiTask;
import org.opengrok.web.api.ApiTaskManager;
import org.opengrok.web.api.v1.RestApp;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusControllerTest extends OGKJerseyTest {
    @Override
    protected DeploymentContext configureDeployment() {
        return ServletDeploymentContext.forServlet(new ServletContainer(new RestApp())).build();
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
    }

    @AfterAll
    static void cleanup() throws InterruptedException {
        ApiTaskManager.getInstance().shutdown();
    }

    private Object doNothing() {
        return null;
    }

    @Test
    void testGetNoUuid() {
        Response response = target(StatusController.PATH)
                .path(UUID.randomUUID().toString())
                .request()
                .get();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void testDeleteNoUuid() {
        Response response = target(StatusController.PATH)
                .path(UUID.randomUUID().toString())
                .request()
                .delete();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void testGet() throws InterruptedException {
        int sleepTime = 3000;
        ApiTask apiTask = new ApiTask("foo", () -> {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }, Response.Status.CREATED);
        String uuidString = apiTask.getUuid().toString();
        ApiTaskManager apiTaskManager = ApiTaskManager.getInstance();
        String poolName = "foo";
        apiTaskManager.addPool(poolName, 1);
        apiTaskManager.submitApiTask(poolName, apiTask);
        Response response = target(StatusController.PATH)
                .path(uuidString)
                .request()
                .get();
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());

        Thread.sleep(sleepTime);
        response = target(StatusController.PATH)
                .path(uuidString)
                .request()
                .get();
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    void testDeleteNotCompleted() {
        int sleepTime = 3000;
        ApiTask apiTask = new ApiTask("foo", () -> {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        });
        String uuidString = apiTask.getUuid().toString();
        ApiTaskManager apiTaskManager = ApiTaskManager.getInstance();
        String poolName = "deleteNotCompleted";
        apiTaskManager.addPool(poolName, 1);
        apiTaskManager.submitApiTask(poolName, apiTask);
        Response response = target(StatusController.PATH)
                .path(uuidString)
                .request()
                .delete();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    void testDelete() throws InterruptedException {
        ApiTask apiTask = new ApiTask("foo", this::doNothing);
        String uuidString = apiTask.getUuid().toString();
        ApiTaskManager apiTaskManager = ApiTaskManager.getInstance();
        String poolName = "deleteCompleted";
        apiTaskManager.addPool(poolName, 1);
        apiTaskManager.submitApiTask(poolName, apiTask);
        Thread.sleep(1000);
        Response response = target(StatusController.PATH)
                .path(uuidString)
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }
}
