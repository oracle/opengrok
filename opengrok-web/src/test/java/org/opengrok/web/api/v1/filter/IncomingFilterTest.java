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
package org.opengrok.web.api.v1.filter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IncomingFilterTest {
    @Before
    public void beforeTest() {
        RuntimeEnvironment.getInstance().setAuthenticationTokens(new HashSet<>());
    }

    @Test
    public void nonLocalhostTestWithValidToken() throws Exception {
        String allowedToken = "foo";

        Set<String> tokens = new HashSet<>();
        tokens.add(allowedToken);
        RuntimeEnvironment.getInstance().setAuthenticationTokens(tokens);

        nonLocalhostTestWithToken(true, allowedToken);
    }

    @Test
    public void nonLocalhostTestWithInvalidToken() throws Exception {
        String allowedToken = "bar";

        Set<String> tokens = new HashSet<>();
        tokens.add(allowedToken);
        RuntimeEnvironment.getInstance().setAuthenticationTokens(tokens);

        nonLocalhostTestWithToken(false, allowedToken + "_");
    }

    @Test
    public void nonLocalhostTestWithTokenChange() throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        String token = "foobar";

        Map<String, String> headers = new TreeMap<>();
        final String authHeaderValue = IncomingFilter.BEARER + token;
        headers.put(HttpHeaders.AUTHORIZATION, authHeaderValue);
        assertTrue(env.getAuthenticationTokens().isEmpty());
        IncomingFilter filter = mockWithRemoteAddress("192.168.1.1", headers, true);

        ContainerRequestContext context = mockContainerRequestContext("test");
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        // No tokens configured.
        filter.filter(context);
        verify(context).abortWith(captor.capture());

        // Setting tokens without refreshing configuration should have no effect.
        Set<String> tokens = new HashSet<>();
        tokens.add(token);
        env.setAuthenticationTokens(tokens);
        filter.filter(context);
        verify(context, times(2)).abortWith(captor.capture());

        // The request should pass only after applyConfig().
        env.applyConfig(false, CommandTimeoutType.RESTFUL);
        context = mockContainerRequestContext("test");
        filter.filter(context);
        verify(context, never()).abortWith(captor.capture());
    }

    private void nonLocalhostTestWithToken(boolean allowed, String token) throws Exception {
        Map<String, String> headers = new TreeMap<>();
        final String authHeaderValue = IncomingFilter.BEARER + token;
        headers.put(HttpHeaders.AUTHORIZATION, authHeaderValue);
        IncomingFilter filter = mockWithRemoteAddress("192.168.1.1", headers, true);

        ContainerRequestContext context = mockContainerRequestContext("test");

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        if (allowed) {
            verify(context, never()).abortWith(captor.capture());
        } else {
            verify(context).abortWith(captor.capture());
        }
    }

    @Test
    public void localhostTestWithForwardedHeader() throws Exception {
        Map<String, String> headers = new TreeMap<>();
        headers.put("X-Forwarded-For", "192.0.2.43, 2001:db8:cafe::17");
        IncomingFilter filter = mockWithRemoteAddress("127.0.0.1", headers, true);

        ContainerRequestContext context = mockContainerRequestContext("test");

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        verify(context).abortWith(captor.capture());
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), captor.getValue().getStatus());
    }

    @Test
    public void nonLocalhostTestWithoutToken() throws Exception {
        IncomingFilter filter = mockWithRemoteAddress("192.168.1.1");

        ContainerRequestContext context = mockContainerRequestContext("test");

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        verify(context).abortWith(captor.capture());

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), captor.getValue().getStatus());
    }

    private IncomingFilter mockWithRemoteAddress(final String remoteAddr, Map<String, String> headers, boolean secure)
            throws Exception {
        IncomingFilter filter = new IncomingFilter();
        filter.init();

        HttpServletRequest request = mock(HttpServletRequest.class);
        for (String name : headers.keySet()) {
            when(request.getHeader(name)).thenReturn(headers.get(name));
        }
        when(request.isSecure()).thenReturn(secure);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);

        setHttpRequest(filter, request);

        return filter;
    }

    private IncomingFilter mockWithRemoteAddress(final String remoteAddr) throws Exception {
        return mockWithRemoteAddress(remoteAddr, new TreeMap<>(), false);
    }

    private void setHttpRequest(final IncomingFilter filter, final HttpServletRequest request) throws Exception {
        Field f = IncomingFilter.class.getDeclaredField("request");
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
        IncomingFilter filter = mockWithRemoteAddress(remoteAddr);

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
        IncomingFilter filter = mockWithRemoteAddress("10.0.0.1");

        ContainerRequestContext context = mockContainerRequestContext("search");

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        verify(context, never()).abortWith(captor.capture());
    }
}
