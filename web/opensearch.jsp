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

ident	"%Z%%M% %I%     %E% SMI"

--%><%@ page import = "java.util.List,
javax.servlet.*,
javax.servlet.http.*,java.util.Iterator,
org.opensolaris.opengrok.configuration.RuntimeEnvironment,
org.opensolaris.opengrok.configuration.Project,
org.opensolaris.opengrok.web.*"
 session="false" errorPage="error.jsp" %><%@ include file="projects.jspf" %><% 
String q    = request.getParameter("q");
String defs = request.getParameter("defs");
String refs = request.getParameter("refs");
String hist = request.getParameter("hist");
String path = request.getParameter("path");
RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
String Context = request.getContextPath();
String laf = environment.getWebappLAF();
StringBuffer url = request.getRequestURL();
url=url.delete(url.lastIndexOf("/"),url.length());
/* TODO  Bug 11749
String proj="project=";
 */
String proj="";
StringBuilder text = new StringBuilder();
boolean firstIteration = true;
if (project != null) {
for (String tproj : project) {
  if (!firstIteration) {
    text.append(',');
  }
  text.append(tproj);
/* TODO  Bug 11749
   proj=proj + Util.URIEncode(tproj)+ ",";
 */
  proj = proj + "project=" + Util.URIEncode(tproj)+ "&amp;";
  firstIteration = false;
 }
}

String projtext = text.toString();

%><?xml version="1.0" encoding="UTF-8"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
<ShortName>OpenGrok <%=projtext%></ShortName>
<Description>Search in OpenGrok <%=projtext%></Description>
<InputEncoding>UTF-8</InputEncoding>
<Image height="16" width="16" type="image/png"><%=url%>/<%=laf%>/img/icon.png</Image><%-- 
<Url type="application/x-suggestions+json" template="suggestionURL"/>
--%><Url template="<%=url%>/s?<%=proj%>q={searchTerms}&amp;start={startPage?}" type="text/html"/>
</OpenSearchDescription>
