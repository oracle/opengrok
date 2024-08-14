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
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.web.DummyHttpServletRequest;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.web.api.v1.RestApp;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provides coverage for the {@link AuthorizationFilter} class.
 */
class AuthorizationFilterTest {
    /**
     * Test that requests that start with API path are let through.
     */
    @Test
    void testApiPath() throws ServletException, IOException {
        AuthorizationFilter filter = new AuthorizationFilter();
        HttpServletRequest request = new DummyHttpServletRequest() {
            @Override
            public String getServletPath() {
                return RestApp.API_PATH + "foo";
            }
        };
        HttpServletResponse response = mock(HttpServletResponse.class);

        FilterChain chain = mock(FilterChain.class);
        // The init() method is currently empty, however it does not hurt to exercise it in case that changes.
        FilterConfig filterConfig = mock(FilterConfig.class);
        filter.init(filterConfig);
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt());
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIsAllowed(boolean isAllowed) throws ServletException, IOException {
        AuthorizationFilter filter = new AuthorizationFilter();
        PageConfig pageConfig = mock(PageConfig.class);
        Project project = mock(Project.class);
        when(pageConfig.getProject()).thenReturn(project);
        when(pageConfig.isAllowed(project)).thenReturn(isAllowed);
        when(pageConfig.getEnv()).thenReturn(RuntimeEnvironment.getInstance());
        HttpServletRequest request = new DummyHttpServletRequest() {
            @Override
            public String getServletPath() {
                return Prefix.DOWNLOAD_P.toString();
            }

            @Override
            public String getRemoteUser() {
                return "user";
            }

            @Override
            public String getRequestURI() {
                return "URI";
            }

            @Override
            public Object getAttribute(String s) {
                if (s.equals(PageConfig.ATTR_NAME)) {
                    return pageConfig;
                }

                return "X";
            }
        };
        HttpServletResponse response = mock(HttpServletResponse.class);

        FilterChain chain = mock(FilterChain.class);
        // The init() method is currently empty, however it does not hurt to exercise it in case that changes.
        FilterConfig filterConfig = mock(FilterConfig.class);
        filter.init(filterConfig);
        filter.doFilter(request, response, chain);

        if (isAllowed) {
            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt());
            verify(response, never()).sendError(anyInt(), anyString());
        } else {
            verify(chain, never()).doFilter(request, response);
            verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Access forbidden");
        }
    }
}