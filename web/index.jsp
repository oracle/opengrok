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

Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.

Portions Copyright 2011 Jens Elkner.

--%><%@ page session="false" errorPage="error.jsp" %><%@

include file="projects.jspf"

%><%
/* ---------------------- index.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    cfg.setTitle("Search");
    String sourceRootPath = cfg.getSourceRootPath();
    if(sourceRootPath == null || sourceRootPath.isEmpty()){
    throw new java.io.FileNotFoundException("Configuration File Not Found");
    }
    File sourceRootPathFile = RuntimeEnvironment.getInstance().getSourceRootFile();
    if(sourceRootPathFile.exists() && !sourceRootPathFile.canRead()){
    throw new java.io.IOException("Can not read configuration file");
    }
}
%><%@

include file="httpheader.jspf"

%><body>
    <div id="page">
        <div id="whole_header">
            <div id="header"><%@

include file="pageheader.jspf"

            %></div>
            <div id="Masthead">OpenGrok search</div>
            <div id="sbar"><%@

include file="menu.jspf"

            %></div>
        </div>
        <div id="results">
            <%= PageConfig.get(request).getEnv().getConfiguration().getBodyIncludeFileContent() %>
        </div>
        <%@

include file="repos.jspf"

        %>
        </div>
<%
/* ---------------------- index.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>