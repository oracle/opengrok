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
 * Copyright (c) 2019, Shenghan Gao <gaoshenghan199123@gmail.com>.
 */
package org.opengrok.web;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;
import org.opengrok.web.api.v1.filter.CorsFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opengrok.web.api.v1.filter.CorsFilter.ALLOW_CORS_HEADER;
import static org.opengrok.web.api.v1.filter.CorsFilter.CORS_REQUEST_HEADER;

class CorsFilterTest {
    @Test
    void nonCorsTest() {
        testBoth(null, null);
    }

    @Test
    void corsTest() {
        testBoth("https://example.org", List.of("*"));
    }

    private void testBoth(String origin, List<Object> headerValue) {
        CorsFilter filter = new CorsFilter();
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getHeaderString(CORS_REQUEST_HEADER)).thenReturn(origin);

        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request, response);
        assertEquals(headerValue, headers.get(ALLOW_CORS_HEADER));
    }

}
