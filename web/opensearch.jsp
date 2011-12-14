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

Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

Portions Copyright 2011 Jens Elkner.

--%><%@page  session="false" errorPage="error.jsp" import="
java.util.Set,

org.opensolaris.opengrok.web.Util"
%><%@

include file="pageconfig.jspf"

%><%@

include file="projects.jspf"

%><%
	/* ---------------------- opensearch.jsp start --------------------- */
{
	cfg = PageConfig.get(request);

	StringBuilder url = new StringBuilder(128);
        String scheme=request.getScheme();
        url.append(scheme).append("://").append(request.getServerName());
	int port = request.getServerPort();
	if ((port != 80 && scheme.equals("http")) ||
               (port != 443 && scheme.equals("https"))) {
		url.append(':').append(port);
	}
	port = url.length();	// mark
	String img = url.append(cfg.getCssDir()).append("/img/icon.png").toString();
	url.setLength(port);	// rewind

	/* TODO  Bug 11749 ??? */
	StringBuilder text = new StringBuilder();
	url.append(request.getContextPath()).append(Prefix.SEARCH_P).append('?');
	Set<String> projects = cfg.getRequestedProjects();
	for (String name : projects) {
		text.append(name).append(',');
		Util.appendQuery(url, "project", name);
	}
	if (text.length() != 0) {
		text.setLength(text.length()-1);
	}
%><?xml version="1.0" encoding="UTF-8"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
	<ShortName>OpenGrok <%= text.toString() %></ShortName>
	<Description>Search in OpenGrok <%= text.toString() %></Description>
	<InputEncoding>UTF-8</InputEncoding>
	<Image height="16" width="16" type="image/png"><%= img %></Image>
<%-- <Url type="application/x-suggestions+json" template="suggestionURL"/>--%>
	<Url template="<%= url.toString() %>&amp;q={searchTerms}" type="text/html"/>
</OpenSearchDescription>
<%
}
/* ---------------------- opensearch.jsp end --------------------- */
%>
