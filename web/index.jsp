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

Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

--%><%@ page import = "java.util.List,
javax.servlet.*,
javax.servlet.http.*,
org.opensolaris.opengrok.configuration.RuntimeEnvironment,
org.opensolaris.opengrok.configuration.Project,
org.opensolaris.opengrok.web.*"
 session="false" errorPage="error.jsp" %><%@ include file="projects.jspf" %><%
String q = null;
String defs = null;
String refs = null;
String hist = null;
String path = null;
String sort = null;
RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
String pageTitle = "Search";
 %><%@ include file="httpheader.jspf" %>
<body>
<div id="page">
<div id="header"><%@ include file="pageheader.jspf" %></div>
<div id="Masthead"></div>
<div id="bar">
<%@ include file="menu.jspf" %>
</div>
<div id="results" style="font-size:100%">
<%@ include file="index_body.html" %>
</div>
<%@include file="foot.jspf"%>
