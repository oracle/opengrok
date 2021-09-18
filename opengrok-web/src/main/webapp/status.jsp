<%--
$Id$

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

Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.
Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.

--%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page session="false" errorPage="error.jsp" import="
org.opengrok.indexer.configuration.RuntimeEnvironment,
org.opengrok.indexer.web.Util"
%><%
{
    PageConfig cfg = PageConfig.get(request);
    cfg.checkSourceRootExistence();
}
%><%@

include file="projects.jspf"

%><%
/* ---------------------- status.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    cfg.setTitle("Status");
}
%><%@

include file="httpheader.jspf"

%>
<body>
    <div id="page">
        <header id="whole_header">
            <%@include file="pageheader.jspf" %>
            <div id="Masthead"></div>
        </header>
        <div id="status">
            <h1>OpenGrok status page</h1>
            <p>
This page is only used for testing purposes to dump some of the
internal settings on your OpenGrok server.</p><%
{
        PageConfig cfg = PageConfig.get(request);
        if (cfg.getEnv().isChattyStatusPage()) {
            Util.dumpConfiguration(out);
        } else {
        %><p>
For security reasons, printing of internal settings is not enabled by
default. To enable, set the property <code>chattyStatusPage</code> to
<code>true</code> in <code>configuration.xml</code>.</p><%
        }
        %>
        </div>
<%
}
/* ---------------------- status.jsp start --------------------- */
%><%@

include file="foot.jspf"

%>