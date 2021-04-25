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
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.util;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebHookTest extends JerseyTest {
    private static final String PREFIX = "service";
    private static int requests;

    @Path(PREFIX)
    public static class Service {
        @POST
        public String handlePost() {
            requests++;
            return "posted";
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected Application configure() {
        return new ResourceConfig(Service.class);
    }

    @Test
    void testPost() throws ExecutionException, InterruptedException {
        assertEquals(0, requests);
        WebHook hook = new WebHook(getBaseUri() + PREFIX, "{}");
        Future<String> future = hook.post();
        future.get();
        assertEquals(1, requests);
    }
}
