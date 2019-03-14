package org.opengrok.web;

import org.junit.Test;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opengrok.web.CorsFilter.ALLOW_CORS_HEADER;

public class CorsFilterTest {
    @Test
    public void nonCorsTest() {
        testBoth(null, null);
    }

    @Test
    public void CorsTest() {
        testBoth("https://example.org", Arrays.asList("*"));
    }

    private void testBoth(String origin, List<Object> headerValue) {
        CorsFilter filter = new CorsFilter();
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getHeaderString("Origin")).thenReturn(origin);

        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request, response);
        assertEquals(headerValue, headers.get(ALLOW_CORS_HEADER));
    }

}
