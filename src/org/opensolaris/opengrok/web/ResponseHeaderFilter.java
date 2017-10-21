/*
 * The contents of this file are Copyright © 2003 Jayson Falkner made available
 * under free license: "All of the code from Servlets and JSP the J2EE Web Tier
 * is freely available. Additonally all of the code the book's code relies on
 * is also freely available. With the exception of Sun's Java Development Kit,
 * everything the book relies on is also open-source. You are encouraged to
 * look at, learn from, and use everything!"
 */

package org.opensolaris.opengrok.web;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;

public class ResponseHeaderFilter implements Filter {
    FilterConfig fc;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res,
        FilterChain chain)
        throws IOException, ServletException {

        HttpServletResponse response = (HttpServletResponse) res;

        // set the provided HTTP response parameters
        for (Enumeration e = fc.getInitParameterNames(); e.hasMoreElements();) {
            String headerName = (String)e.nextElement();
            response.addHeader(headerName, fc.getInitParameter(headerName));
        }

        // pass the request/response on
        chain.doFilter(req, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        this.fc = filterConfig;
    }

    @Override
    public void destroy() {
        this.fc = null;
    }
}
