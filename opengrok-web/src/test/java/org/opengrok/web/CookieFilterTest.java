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
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class CookieFilterTest {
    static class DummyHttpServletResponse implements HttpServletResponse {

        @Override
        public void addCookie(Cookie cookie) {

        }

        @Override
        public boolean containsHeader(String s) {
            return false;
        }

        @Override
        public String encodeURL(String s) {
            return null;
        }

        @Override
        public String encodeRedirectURL(String s) {
            return null;
        }

        @Override
        @Deprecated
        public String encodeUrl(String s) {
            return null;
        }

        @Override
        @Deprecated
        public String encodeRedirectUrl(String s) {
            return null;
        }

        @Override
        public void sendError(int i, String s) throws IOException {

        }

        @Override
        public void sendError(int i) throws IOException {

        }

        @Override
        public void sendRedirect(String s) throws IOException {

        }

        @Override
        public void setDateHeader(String s, long l) {

        }

        @Override
        public void addDateHeader(String s, long l) {

        }

        private Map<String, List<String>> headers = new HashMap<>();

        @Override
        public void setHeader(String s, String s1) {
            headers.clear();
            List<String> list = new ArrayList<>();
            list.add(s1);
            headers.put(s, list);
        }

        @Override
        public void addHeader(String s, String s1) {
            List<String> list = headers.get(s);
            if (list == null) {
                list = new ArrayList<>();
                headers.put(s, list);
            }
            headers.get(s).add(s1);
        }

        @Override
        public void setIntHeader(String s, int i) {

        }

        @Override
        public void addIntHeader(String s, int i) {

        }

        @Override
        public void setStatus(int i) {

        }

        @Override
        @Deprecated
        public void setStatus(int i, String s) {

        }

        @Override
        public int getStatus() {
            return 0;
        }

        @Override
        public String getHeader(String s) {
            return null;
        }

        @Override
        public Collection<String> getHeaders(String s) {
            return headers.get(s);
        }

        @Override
        public Collection<String> getHeaderNames() {
            return null;
        }

        @Override
        public String getCharacterEncoding() {
            return null;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return null;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            return null;
        }

        @Override
        public void setCharacterEncoding(String s) {

        }

        @Override
        public void setContentLength(int i) {

        }

        @Override
        public void setContentLengthLong(long l) {

        }

        @Override
        public void setContentType(String s) {

        }

        @Override
        public void setBufferSize(int i) {

        }

        @Override
        public int getBufferSize() {
            return 0;
        }

        @Override
        public void flushBuffer() throws IOException {

        }

        @Override
        public void resetBuffer() {

        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void reset() {

        }

        @Override
        public void setLocale(Locale locale) {

        }

        @Override
        public Locale getLocale() {
            return null;
        }
    }

    @Test
    public void testNoHeaders() throws IOException, ServletException {
        CookieFilter filter = new CookieFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = new DummyHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        FilterConfig filterConfig = mock(FilterConfig.class);
        filter.init(filterConfig);
        filter.doFilter(request, response, chain);

        assertNull(response.getHeaders(HttpHeaders.SET_COOKIE));
    }

    @Test
    public void doTest() throws IOException, ServletException {
        CookieFilter filter = new CookieFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = new DummyHttpServletResponse();

        String[] headerValues = new String[]{"foo=bar", "Tessier=Ashpool"};
        for (String value: headerValues) {
            response.addHeader(HttpHeaders.SET_COOKIE, value);
        }

        FilterChain chain = mock(FilterChain.class);
        FilterConfig filterConfig = spy(FilterConfig.class);
        Map<String, String> m = new HashMap<>();
        m.put("Bonnie", "Clyde");
        m.put("Porgy", "Bess");
        m.put("Empty", "");
        doReturn(Collections.enumeration(m.keySet())).when(filterConfig).getInitParameterNames();
        for (String key: m.keySet()) {
            doReturn(m.get(key)).when(filterConfig).getInitParameter(key);
        }

        filter.init(filterConfig);
        filter.doFilter(request, response, chain);

        // Mimic what the filter should be doing.
        Set<String> expectedHeaderValues = new HashSet<>();
        for (String headerValue: headerValues) {
            String suffix = m.keySet().stream().map(s -> m.get(s).isEmpty() ?
                    s : s + "=" + m.get(s)).collect(Collectors.joining("; "));
            expectedHeaderValues.add(headerValue + "; " + suffix);
        }
        assertEquals(expectedHeaderValues, new HashSet<>(response.getHeaders(HttpHeaders.SET_COOKIE)));
    }
}
