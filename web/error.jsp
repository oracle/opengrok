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

Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

--%><%@ page import = "javax.servlet.*,
javax.servlet.http.*,
java.io.*,
org.opensolaris.opengrok.configuration.*"
%><%@ page session="false" %><%@ page isErrorPage="true" %><%
String context = request.getContextPath();
RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
environment.setUrlPrefix(context + "/s?");
environment.register();
String rawSource = environment.getSourceRootPath();
String configError = "";
if ("".equals(rawSource)) {
    configError = "SRC_ROOT parameter has not been configured in web.xml! Please configure your webapp.";
} else {
    if (environment.getSourceRootFile() == null || !environment.getSourceRootFile().isDirectory()) {
        configError = "SRC_ROOT parameter in web.xml does not point to a valid directory! Please configure your webapp.";
    }
}
String pageTitle = "Error!";
%><%@ include file="httpheader.jspf" %>
<body>
<div id="page">
    <div id="header">
      <%@ include file="pageheader.jspf" %>
    </div>
<div id="Masthead"></div>
<div id="bar"><a id="home" href="<%=context%>">Home</a> | <input id="search" name="q" class="q"/> <input type="submit" value="Search" class="submit"/> </div>
<h3 class="error">There was an error!</h3>
<p><%=configError%>
</p><%if (!(exception instanceof NullPointerException)) {%><pre><%
   StringWriter wrt = new StringWriter();
   PrintWriter prt = new PrintWriter(wrt);
   exception.printStackTrace(prt);
   prt.flush();
   out.write(wrt.toString());
   prt.close();
%>
</pre>
<p>
<%=exception.getMessage()%>
</p>
<%
}
%>
<%@include file="foot.jspf"%>
