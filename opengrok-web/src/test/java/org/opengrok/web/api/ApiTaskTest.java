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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTaskTest {

    private Object doNothing() {
        return null;
    }

    @Test
    void testConstructorBasic() {
        ApiTask apiTask = new ApiTask("foo", this::doNothing);
        assertFalse(apiTask.isCompleted());
    }

    @Test
    void testConstructorResponseStatus() {
        Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
        ApiTask apiTask = new ApiTask("bar", this::doNothing, status);
        assertEquals(status, apiTask.getResponseStatus());
    }

    private static class Task {
        private int value;

        Task() {
            value = 1;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    @Test
    void testCallable() throws Exception {
        Task task = new Task();
        int newValue = task.getValue() ^ 1;
        ApiTask apiTask = new ApiTask("foo", () -> { task.setValue(newValue); return newValue; });
        assertFalse(apiTask.isCompleted());
        assertFalse(apiTask.isDone());
        apiTask.getCallable().call();
        assertEquals(newValue, task.getValue());
        assertTrue(apiTask.isCompleted());
    }

    @Test
    void testEarlyGetResponse() {
        ApiTask apiTask = new ApiTask("early", () -> null);
        assertThrows(IllegalStateException.class, apiTask::getResponse);
    }

    @Test
    void testAlreadySubmitted() {
        ApiTask apiTask = new ApiTask("foo", this::doNothing);
        apiTask.setSubmitted();
        assertThrows(IllegalStateException.class, apiTask::getCallable);
    }
}
