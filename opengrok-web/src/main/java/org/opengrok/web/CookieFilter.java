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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;

/**
 * Makes sure that all cookies originating from the web application have the Same-site attribute set.
 */
public class CookieFilter implements Filter {
    private FilterConfig fc;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse response = (HttpServletResponse) res;

        chain.doFilter(req, response);

        // Change the existing cookies to use the attributes and values from the configuration.
        String cookieName = HttpHeaders.SET_COOKIE;
        Collection<String> headers = response.getHeaders(cookieName);
        if (headers == null) {
            return;
        }
        boolean firstHeader = true;
        String suffix = getSuffix();
        for (String header : headers) { // there can be multiple Set-Cookie attributes
            if (firstHeader) {
                response.setHeader(cookieName, String.format("%s; %s", header, suffix));
                firstHeader = false;
                continue;
            }
            response.addHeader(cookieName, String.format("%s; %s", header, suffix));
        }
    }

    private String getSuffix() {
        StringBuilder sb = new StringBuilder();

        for (Enumeration<String> e = fc.getInitParameterNames(); e.hasMoreElements();) {
            String attributeName = e.nextElement();
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(attributeName);
            String attributeValue = fc.getInitParameter(attributeName);
            if (!attributeValue.isEmpty()) {
                sb.append("=");
                sb.append(attributeValue);
            }
        }
        return sb.toString();
    }

    @Override
    public void init(FilterConfig filterConfig) {
        this.fc = filterConfig;
    }

    @Override
    public void destroy() {
        // pass
    }
}
