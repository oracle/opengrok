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

Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

--%><%@ page import = "java.util.List,
javax.servlet.*,
javax.servlet.http.*,
org.opensolaris.opengrok.configuration.RuntimeEnvironment,
org.opensolaris.opengrok.configuration.Project,
org.opensolaris.opengrok.history.HistoryGuru,
org.opensolaris.opengrok.web.*"
 session="false" errorPage="error.jsp" %><%@ include file="projects.jspf" %><%
RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
environment.register();
HistoryGuru historyGuru = HistoryGuru.getInstance();
String pageTitle = "Status";
 %><%@ include file="httpheader.jspf" %>
<body>
<div id="page">
<div id="header"><%@ include file="pageheader.jspf" %></div>
<div id="Masthead"></div>
<div id="bar">
    <h1>OpenGrok status page</h1>
    <p>
        This page is only used for testing purposes to dump some of the
        internal settings on your OpenGrok server.
    </p>
    <table border="1" width="100%">
        <tr>
            <th>Variable</th><th>Value</th>
        </tr>
        <tr>
            <td>Source root</td><td><%=environment.getSourceRootPath()%></td>
        </tr>
        <tr>
            <td>Data root</td><td><%=environment.getDataRootPath()%></td>
        </tr>
        <tr>
            <td>CTags</td><td><%=environment.getCtags()%></td>
        </tr>
        <tr>
            <td>Bug page</td><td><%=environment.getBugPage()%></td>
        </tr>
        <tr>
            <td>Bug pattern</td><td><%=environment.getBugPattern()%></td>
        </tr>
        <tr>
            <td>User page</td><td><%=environment.getUserPage()%></td>
        </tr>
        <tr>
            <td>Review page</td><td><%=environment.getReviewPage()%></td>
        </tr>
        <tr>
            <td>Review pattern</td><td><%=environment.getReviewPattern()%></td>
        </tr>
        <tr>
            <td>Using projects</td><td><%=environment.hasProjects()%></td>
        </tr>
        <tr>
            <td>Ignored files</td><td><ul><%
            for (String s : environment.getIgnoredNames().getItems()) {
              %><li><%=s%></li><%
            }
            %></ul></td>
        </tr>
        <tr>
            <td>Index word limit</td><td><%=environment.getIndexWordLimit()%></td>
        </tr>
        <tr>
            <td>Allow leading wildcard in search</td><td><%=environment.isAllowLeadingWildcard()%></td>
        </tr>
        <tr>
            <td>History cache</td>
            <td><%=Util.htmlize(historyGuru.getCacheInfo())%></td>
        </tr>

    </table>
</div>
<%@include file="foot.jspf"%>
