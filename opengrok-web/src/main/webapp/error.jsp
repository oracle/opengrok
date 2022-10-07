<%--
CDDL HEADER START

The contents of this file are subject to the terms of the
Common Development and Distribution License (the "License").
You may not use this file except in compliance with the License.

See LICENSE.txt included in this distribution for the specific
language governing permissions and limitations under the License.

When distributing Covered Code, include this CDDL HEADER in each
file and include the License file at LICENSE.txt.
If applicable, add the following below this CDDL HEADER, with the
fields enclosed by brackets "[]" replaced with your own identifying
information: Portions Copyright [yyyy] [name of copyright owner]

CDDL HEADER END

Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.
Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.

--%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page import="jakarta.servlet.http.HttpServletResponse"%>
<%@ page session="false" isErrorPage="true" import="
java.io.PrintWriter,
java.io.StringWriter,

org.opengrok.indexer.web.Util"
%><%
/* ---------------------- error.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    cfg.setTitle("Error!");

    // Set status to Internal error. This should help to avoid caching
    // the page by some proxies.
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

    String context = request.getContextPath();
}
%><%@

include file="/httpheader.jspf"

%>
<body>
<div id="page">
    <header id="whole_header">
        <%@include file="/pageheader.jspf" %>
    </header>
<%
{
    PageConfig cfg = PageConfig.get(request);
    String configError = "";
    if (cfg.getSourceRootPath() == null || cfg.getSourceRootPath().isEmpty()) {
        configError = "The source root path has not been configured! "
            + "Please configure your webapp.";
    } else if (!cfg.getEnv().getSourceRootFile().isDirectory()) {
        configError = "The source root " +  cfg.getEnv().getSourceRootPath()
            + " specified in your configuration does "
            + "not point to a valid directory! Please configure your webapp.";
    }
%><h3 class="error">There was an error!</h3>
    <p class="error"><%= configError %></p><%
    if (exception != null) {
%>
        <p class="error"><%= exception.getMessage() %></p>
        <pre><%
        StringWriter wrt = new StringWriter();
        PrintWriter prt = new PrintWriter(wrt);
        exception.printStackTrace(prt);
        prt.close();
        out.write(Util.htmlize(wrt.toString()));
        %></pre><%
    } else {
        %><p class="error">Unknown Error</p><%
    }
}
/* ---------------------- error.jsp end --------------------- */
%><%@

include file="/foot.jspf"

%>
