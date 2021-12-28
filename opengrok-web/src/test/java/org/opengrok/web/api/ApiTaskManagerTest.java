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

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTaskManagerTest {
    @Test
    void testSingleton() {
        assertNotNull(ApiTaskManager.getInstance());
    }

    @Test
    void testQueueName() {
        String name = "foo";
        assertEquals(name, ApiTaskManager.getQueueName(name));
        name = "/foo";
        assertEquals(name.substring(1), ApiTaskManager.getQueueName(name));
    }

    private void doNothing() {
    }

    @Test
    void testTaskInvalidQueue() {
        ApiTaskManager apiTaskManager = ApiTaskManager.getInstance();
        ApiTask apiTask = new ApiTask("foo", this::doNothing);
        assertEquals(Response.Status.BAD_REQUEST,
                apiTaskManager.submitApiTask("/nonexistent", apiTask).getStatusInfo());
    }

    @Test
    void testTaskSubmitDelete() {
        ApiTaskManager apiTaskManager = ApiTaskManager.getInstance();
        String name = "foo";
        apiTaskManager.addPool(name, 1);
        ApiTask apiTask = new ApiTask("foo", this::doNothing);
        Response response = apiTaskManager.submitApiTask(name, apiTask);
        assertEquals(Response.Status.ACCEPTED, response.getStatusInfo());
        String location = response.getHeaderString(HttpHeaders.LOCATION);
        assertNotNull(location);
        String uuidString = apiTask.getUuid().toString();
        assertTrue(location.contains(uuidString));
        assertSame(apiTask, apiTaskManager.getApiTask(uuidString));
        apiTaskManager.deleteApiTask(uuidString);
        assertNull(apiTaskManager.getApiTask(uuidString));
    }

    @Test
    void testTaskInvalidUuid() {
        ApiTaskManager apiTaskManager = ApiTaskManager.getInstance();
        assertThrows(IllegalArgumentException.class, () -> apiTaskManager.getApiTask("foo"));
    }

    @Test
    void testDuplicateQueueAdd() {
        ApiTaskManager apiTaskManager = ApiTaskManager.getInstance();
        String name = "myQueue";
        apiTaskManager.addPool(name, 1);
        assertThrows(IllegalStateException.class, () -> apiTaskManager.addPool(name, 1));
    }

    @Test
    void testShutdown() throws InterruptedException {
        ApiTaskManager apiTaskManager = ApiTaskManager.getInstance();
        String name = "bar";
        apiTaskManager.addPool(name, 1);
        apiTaskManager.shutdown();
        ApiTask apiTask = new ApiTask("nada", this::doNothing);
        assertThrows(RejectedExecutionException.class, () -> apiTaskManager.submitApiTask(name, apiTask));
    }
}
