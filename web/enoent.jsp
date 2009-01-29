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

ident	"@(#)enoent.jsp 1.3     05/12/02 SMI"

--%><%@ page import = "javax.servlet.*,
javax.servlet.http.*,
java.lang.*,
java.io.*,
org.opensolaris.opengrok.configuration.*"
 session="false" %><%@ page isErrorPage="true" %><%

String context = request.getContextPath();
RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
environment.setUrlPrefix(context + "/s?");
environment.register();
String rawSource = environment.getSourceRootPath();
String configError = "";
if (rawSource.equals("")) {
    configError = "CONFIGURATION parameter has not been configured in web.xml! Please configure your webapp.";
} else {
    if (!environment.getSourceRootFile().isDirectory()) {
        configError = "The source root specified in your configuration does not point to a valid directory! Please configure your webapp.";
    }
}
String pageTitle = "File not found";
%><%@ include file="httpheader.jspf" %>
<body><div id="page">
<form action="<%=context%>/search">
    <div id="header">
        <%@ include file="pageheader.jspf" %>
    </div>
<div id="Masthead"></div>
<div id="bar"><a id="home" href="<%=context%>">Home</a> | <input id="search" name="q" class="q"/> <input type="submit" value="Search" class="submit"/> </div>
<h3 class="error">Error 404: File not found!</h3>
The requested resource is not available. <%=configError%>
<div style="display:block;height:10em">&nbsp;</div><%@include file="foot.jspf"%>
