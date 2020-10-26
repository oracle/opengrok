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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.filter;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LocalhostFilterTest {

    @Test
    public void nonLocalhostTest() throws Exception {
        LocalhostFilter filter = mockWithRemoteAddress("192.168.1.1");

        ContainerRequestContext context = mockContainerRequestContext("test");

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        verify(context).abortWith(captor.capture());

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), captor.getValue().getStatus());
    }

    private LocalhostFilter mockWithRemoteAddress(final String remoteAddr) throws Exception {
        LocalhostFilter filter = new LocalhostFilter();
        filter.init();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);

        setHttpRequest(filter, request);

        return filter;
    }

    private void setHttpRequest(final LocalhostFilter filter, final HttpServletRequest request) throws Exception {
        Field f = LocalhostFilter.class.getDeclaredField("request");
        f.setAccessible(true);
        f.set(filter, request);
    }

    private ContainerRequestContext mockContainerRequestContext(final String path) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        UriInfo info = mock(UriInfo.class);

        when(info.getPath()).thenReturn(path);

        when(context.getUriInfo()).thenReturn(info);

        return context;
    }

    @Test
    public void localhostTest() throws Exception {
        assertFilterDoesNotBlockAddress("127.0.0.1");
    }

    private void assertFilterDoesNotBlockAddress(final String remoteAddr) throws Exception {
        LocalhostFilter filter = mockWithRemoteAddress(remoteAddr);

        ContainerRequestContext context = mockContainerRequestContext("test");

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        verify(context, never()).abortWith(captor.capture());
    }

    @Test
    public void localhostIPv6Test() throws Exception {
        assertFilterDoesNotBlockAddress("0:0:0:0:0:0:0:1");
    }

    @Test
    public void searchTest() throws Exception {
        LocalhostFilter filter = mockWithRemoteAddress("10.0.0.1");

        ContainerRequestContext context = mockContainerRequestContext("search");

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        verify(context, never()).abortWith(captor.capture());
    }

}
