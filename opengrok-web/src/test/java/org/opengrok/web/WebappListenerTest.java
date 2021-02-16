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
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebappListenerTest {
    /**
     * Simple smoke test for WebappListener request handling.
     */
    @Test
    public void testRequest() {
        WebappListener wl = new WebappListener();
        final HttpServletRequest req = mock(HttpServletRequest.class);
        final ServletContext servletContext = mock(ServletContext.class);
        when(req.getServletContext()).thenReturn(servletContext);
        ServletRequestEvent event = new ServletRequestEvent(servletContext, req);

        wl.requestInitialized(event);
        wl.requestDestroyed(event);
    }
}
