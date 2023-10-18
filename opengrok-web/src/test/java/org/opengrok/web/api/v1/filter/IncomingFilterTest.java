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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncomingFilterTest {
    @BeforeEach
    void beforeTest() {
        RuntimeEnvironment.getInstance().setAuthenticationTokens(new HashSet<>());
    }

    @Test
    void nonLocalhostTestWithValidToken() throws Exception {
        String allowedToken = "foo";

        Set<String> tokens = new HashSet<>();
        tokens.add(allowedToken);
        RuntimeEnvironment.getInstance().setAuthenticationTokens(tokens);

        nonLocalhostTestWithToken(true, allowedToken);
    }

    @Test
    void nonLocalhostTestWithInvalidToken() throws Exception {
        String allowedToken = "bar";

        Set<String> tokens = new HashSet<>();
        tokens.add(allowedToken);
        RuntimeEnvironment.getInstance().setAuthenticationTokens(tokens);

        nonLocalhostTestWithToken(false, allowedToken + "_");
    }

    @Test
    void nonLocalhostTestWithTokenChange() throws Exception {
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
    void localhostTestWithForwardedHeader() throws Exception {
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
    void nonLocalhostTestWithoutToken() throws Exception {
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
    void localhostTest() throws Exception {
        assertFilterDoesNotBlockAddress("127.0.0.1", "test");
    }

    private void assertFilterDoesNotBlockAddress(final String remoteAddr, final String url) throws Exception {
        IncomingFilter filter = mockWithRemoteAddress(remoteAddr);

        ContainerRequestContext context = mockContainerRequestContext(url);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        verify(context, never()).abortWith(captor.capture());
    }

    @Test
    void localhostIPv6Test() throws Exception {
        assertFilterDoesNotBlockAddress("0:0:0:0:0:0:0:1", "test");
    }

    @Test
    void searchTest() throws Exception {
        assertFilterDoesNotBlockAddress("10.0.0.1", "search");
    }

    @Test
    void systemPingRemoteWithoutTokenTest() throws Exception {
        assertFilterDoesNotBlockAddress("10.0.0.1", "system/ping");
    }

    @Test
    void systemPathDescWithoutTokenTest() throws Exception {

        IncomingFilter filter = mockWithRemoteAddress("192.168.1.1");

        ContainerRequestContext context = mockContainerRequestContext("system/pathdesc");

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

        filter.filter(context);

        verify(context).abortWith(captor.capture());

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), captor.getValue().getStatus());
    }
}
